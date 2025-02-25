/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.bulk;

import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_DOCUMENTS_COUNT;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_EVALUATION_DATA;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TRAINING_DATA;
import static org.nuxeo.ai.bulk.ExportHelper.getAvroCodec;
import static org.nuxeo.ai.bulk.ExportHelper.getKVS;
import static org.nuxeo.ecm.core.api.CoreInstance.getCoreSessionSystem;
import static org.nuxeo.ai.bulk.RecordWriterBatchComputation.TRAINING_WRITER;
import static org.nuxeo.ai.bulk.RecordWriterBatchComputation.VALIDATION_WRITER;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ai.pipes.types.ExportStatus;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Fetches and Updates a Dataset related to received batch
 */
public class DatasetUpdateComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(DatasetUpdateComputation.class);

    protected Map<String, Long> counters = Collections.synchronizedMap(new HashMap<>());

    protected Map<String, Long> errors = Collections.synchronizedMap(new HashMap<>());

    public DatasetUpdateComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void processRecord(ComputationContext ctx, String input, Record record) {
        ExportRecord export = getAvroCodec(ExportRecord.class).decode(record.getData());
        if (export.isFailed()) {
            errors.putIfAbsent(export.getId(), 0L);
            errors.computeIfPresent(export.getId(), (s, val) -> 1L + val);
        }

        boolean endOfBatch = false;
        try {
            endOfBatch = isEndOfBatch(export);
        } catch (NuxeoException e) {
            log.error("Likely the KVS record was removed by TTL expired event; interrupting {} pipeline",
                    export.getCommandId());
            ctx.askForTermination();
            return;
        }

        if (endOfBatch) {
            BulkService service = Framework.getService(BulkService.class);
            String commandId = export.getCommandId();
            BulkCommand command = service.getCommand(commandId);
            if (command == null) {
                log.warn("The bulk command {} is missing. Unable to save blobs info.", commandId);
            }
            log.debug("Ending batch for {}", commandId);

            for (String name : Arrays.asList(TRAINING_WRITER, VALIDATION_WRITER)) {
                RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(name);
                if (writer == null) {
                    throw new NuxeoException("Unable to find record writer: " + name);
                }

                String blobId = export.getId();
                if (writer.exists(blobId)) {
                    try {
                        writer.complete(blobId).ifPresent(blob -> {
                            if (command != null) {
                                updateDatasetDocument(command, blob, TRAINING_WRITER.equals(name), export);
                            }
                        });
                    } catch (IOException e) {
                        throw new NuxeoException("Unable to complete action " + commandId, e);
                    }
                } else {
                    log.warn("No writer file exists for command {} name {} blob ID {}", commandId, name, blobId);
                }
            }

            long processed = counters.get(export.getId());
            long errored = errors.computeIfAbsent(export.getId(), (key) -> 0L);
            ExportStatus eb = ExportStatus.of(commandId, export.getId(), processed, errored);
            eb.setTraining(export.isTraining());

            ctx.produceRecord(OUTPUT_1, export.getId(), getAvroCodec(ExportStatus.class).encode(eb));
            // Clear counter
            counters.remove(export.getId());
            ctx.askForCheckpoint();
        }

        updateDelta(export.getId());

    }

    private void updateDatasetDocument(BulkCommand cmd, Blob blob, boolean isTraining, ExportRecord export) {
        TransactionHelper.runInTransaction(() -> {
            CoreSession session = getCoreSessionSystem(cmd.getRepository(), cmd.getUsername());
            DocumentModel document = Framework.getService(DatasetExportService.class)
                                              .getCorpusOfBatch(session, export.getCommandId(), export.getId());

            if (document != null) {
                log.debug("Updating document {} with blob {}", document.getId(), blob.getDigest());

                document.setPropertyValue(DATASET_EXPORT_DOCUMENTS_COUNT, getCount(export.getId()));

                String prop = isTraining ? DATASET_EXPORT_TRAINING_DATA : DATASET_EXPORT_EVALUATION_DATA;
                document.setPropertyValue(prop, (Serializable) blob);
                session.saveDocument(document);
            } else {
                log.warn("Unable to save blob {} for command {}.", blob.getDigest(), export.getCommandId());
                throw new NuxeoException("Unable to find DatasetExport with command " + export.getCommandId());
            }
            return null;
        });
    }

    protected Long getCount(String id) {
        return counters.get(id);
    }

    protected void updateDelta(String key) {
        counters.computeIfPresent(key, (s, val) -> 1L + val);
    }

    protected boolean isEndOfBatch(ExportRecord rec) {
        Long processed = counters.computeIfAbsent(rec.getId(), (key) -> 1L);
        Long total = getKVS().getLong(rec.getId());

        if (total == null) {
            throw new NuxeoException("Entries total is not existing anymore in the KVS");
        }

        return processed >= total;
    }
}
