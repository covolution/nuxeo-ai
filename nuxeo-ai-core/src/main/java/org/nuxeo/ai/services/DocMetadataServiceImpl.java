/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.services;

import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL_VERSION;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_CONFIDENCE;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABEL;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABELS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;
import static org.nuxeo.ai.pipes.events.DirtyEventListener.DIRTY_EVENT_NAME;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.enrichment.EnrichedPropertiesEventListener;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.services.PipelineService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.services.config.ConfigurationService;

/**
 * An implementation of DocMetadataService
 */
public class DocMetadataServiceImpl extends DefaultComponent implements DocMetadataService {

    public static final String ENRICHMENT_ADDED = "ENRICHMENT_ADDED";

    public static final String ENRICHMENT_USING_FACETS = "nuxeo.enrichment.facets.inUse";

    private static final Log log = LogFactory.getLog(DocMetadataServiceImpl.class);

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        if (Framework.getService(ConfigurationService.class).isBooleanTrue(ENRICHMENT_USING_FACETS)) {
            // Facets are being used so lets clean it up as well.
            Framework.getService(PipelineService.class)
                     .addEventListener(DIRTY_EVENT_NAME, false, false, new EnrichedPropertiesEventListener());
        }
    }

    @Override
    public DocumentModel saveEnrichment(CoreSession session, EnrichmentMetadata metadata) {
        // TODO: Handle versions here?
        DocumentModel doc;
        try {
            doc = session.getDocument(new IdRef(metadata.context.documentRef));
        } catch (DocumentNotFoundException e) {
            log.info("Unable to save enrichment data for missing doc " + metadata.context.documentRef);
            return null;
        }

        Map<String, Object> anItem = createEnrichment(metadata);

        if (anItem != null) {
            if (!doc.hasFacet(ENRICHMENT_FACET)) {
                doc.addFacet(ENRICHMENT_FACET);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichmentList = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
            if (enrichmentList == null) {
                enrichmentList = new ArrayList<>(1);
            }
            enrichmentList.add(anItem);
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, enrichmentList);
            doc.putContextData(ENRICHMENT_ADDED, Boolean.TRUE);
        }
        return doc;
    }

    /**
     * Create a enrichment Map using the enrichment metadata
     */
    protected Map<String, Object> createEnrichment(EnrichmentMetadata metadata) {

        List<Map<String, Object>> suggestions = new ArrayList<>(metadata.getLabels().size());
        metadata.getLabels().forEach(suggestion -> {
            Map<String, Object> anEntry = new HashMap<>();
            anEntry.put(SUGGESTION_PROPERTY, suggestion.getProperty());
            List<Map<String, Object>> values = new ArrayList<>(suggestion.getValues().size());
            suggestion.getValues().forEach(value -> {
                Map<String, Object> val = new HashMap<>(2);
                val.put(SUGGESTION_LABEL, value.getName());
                val.put(SUGGESTION_CONFIDENCE, value.getConfidence());
                values.add(val);
            });
            anEntry.put(SUGGESTION_LABELS, values);
            suggestions.add(anEntry);
        });

        Map<String, Object> anEntry = new HashMap<>();
        AIComponent aiComponent = Framework.getService(AIComponent.class);

        if (!suggestions.isEmpty()) {
            anEntry.put(SUGGESTION_SUGGESTIONS, suggestions);
        }

        Blob metaDataBlob;
        Blob rawBlob = null;
        try {
            if (StringUtils.isNotEmpty(metadata.getRawKey())) {
                TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentService(
                        metadata.getModelName());
                List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
                if (rawBlobs != null && rawBlobs.size() == 1) {
                    rawBlob = rawBlobs.get(0);
                } else {
                    log.warn(String.format("Unexpected transient store raw blob information for %s. "
                            + "A single raw blob is expected.", metadata.getModelName()));
                }
            }
            metaDataBlob = Blobs.createJSONBlob(MAPPER.writeValueAsString(metadata));
        } catch (IOException e) {
            throw new NuxeoException("Unable to process metadata blob", e);
        }

        anEntry.put(ENRICHMENT_MODEL, metadata.getModelName());
        anEntry.put(ENRICHMENT_INPUT_DOCPROP_PROPERTY, metadata.context.inputProperties);
        anEntry.put(ENRICHMENT_RAW_KEY_PROPERTY, rawBlob);
        anEntry.put(NORMALIZED_PROPERTY, metaDataBlob);
        if (metadata.getModelVersion() != null) {
            anEntry.put(ENRICHMENT_MODEL_VERSION, metadata.getModelVersion());
        }
        if (log.isDebugEnabled()) {
            log.debug(String.format("Enriching doc %s with %s", metadata.context.documentRef, suggestions));
        }
        return anEntry;
    }

}
