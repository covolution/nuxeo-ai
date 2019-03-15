/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.enrichment;

import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentService.MINIMUM_CONFIDENCE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.textract.TextractService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.BoundingBox;

import net.jodah.failsafe.RetryPolicy;

/**
 * Analyzes text in a document using AWS TextractService.
 */
public class AnalyzeDocumentEnrichmentService extends AbstractEnrichmentService implements EnrichmentCachable {

    public static final String FEATURES_OPTION = "features";

    public static final String DEFAULT_CONFIDENCE = "70";

    public static final String DEFAULT_FEATURES = "TABLES,FORMS";

    private static final Logger log = LogManager.getLogger(DetectDocumentTextEnrichmentService.class);

    protected float minConfidence;

    protected String[] features;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        String featuresList = descriptor.options.getOrDefault(FEATURES_OPTION, DEFAULT_FEATURES);
        if (StringUtils.isNotBlank(featuresList)) {
            features = featuresList.split(",");
        }
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                AnalyzeDocumentResult result =
                        Framework.getService(TextractService.class).analyzeDocument(blob.getValue(), features);
                if (result != null && !result.getBlocks().isEmpty()) {
                    enriched.addAll(processResults(blobTextFromDoc, blob.getKey(), result.getBlocks()));
                }
            }
            return enriched;
        });
    }

    /**
     * Process the result of the call
     */
    protected Collection<? extends EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc,
                                                                      String propName,
                                                                      List<Block> blocks) {
        List<AIMetadata.Tag> tags;
        if (Framework.getService(TextractService.class).preprocessResults()) {
            tags = blocks
                    .stream()
                    .map(this::tagFromBlock)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            tags = Collections.emptyList();
        }

        String raw = toJsonString(jg -> jg.writeObjectField("blocks", blocks));

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                                                 .withTags(tags)
                                                 .withRawKey(rawKey)
                                                 .withDocumentProperties(singleton(propName))
                                                 .build());
    }

    protected AIMetadata.Tag tagFromBlock(Block block) {

        if (log.isTraceEnabled()) {
            log.trace(AWSHelper.getInstance().debugTextractBlock(block));
        }

        //TODO:  DO THIS PROCESSING OR NOT?
//PAGE - Contains a list of child Block objects that are detected on a specific page.
//KEY_VALUE_SET - Stores the KEY and VALUE Block objects for a field that's detected in a document.
// Use the EntityType field to determine if a KEY_VALUE_SET object is a KEY Block object or a VALUE Block object.
//WORD - One or more ISO basic Latin script characters that aren't separated by spaces.
//LINE - A string of tab-delimited, contiguous words.
//TABLE - A table that's detected in the document.
//CELL - A cell within a detected table. The cell is the parent of the block that contains the text in the cell.
        switch (block.getBlockType()) {
            case "PAGE":
                log.info("Page is " + block.getPage());
                break;
            case "TABLE":
                if (block.getConfidence() >= minConfidence) {
                    BoundingBox box = block.getGeometry().getBoundingBox();
                    return new AIMetadata.Tag("TABLE", kind, block.getBlockType(),
                                              new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box
                                                      .getTop()),
                                              Collections.emptyList(),
                                              block.getConfidence() / 100
                    );
                }
                break;
            default:
                log.warn("Unrecognised type " + block.getBlockType());
        }
        return null;
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return new RetryPolicy()
                .abortOn(NuxeoException.class, FatalEnrichmentError.class)
                .withMaxRetries(2)
                .withBackoff(10, 60, TimeUnit.SECONDS);
    }
}
