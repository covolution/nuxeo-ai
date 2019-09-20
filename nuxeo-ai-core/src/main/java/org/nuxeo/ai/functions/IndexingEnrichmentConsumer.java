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
package org.nuxeo.ai.functions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Hmm, what do we do about Document updates?  How to delete tags on update?
 */
public class IndexingEnrichmentConsumer extends AbstractEnrichmentConsumer implements Initializable {

    private static final Log log = LogFactory.getLog(IndexingEnrichmentConsumer.class);

    protected String indexName;

    protected String indexType;

    @Override
    public void init(Map<String, String> options) {
        indexType = options.getOrDefault("indexType", "aitag");
    }

    protected ESClient getClient() {
        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        ESClient client = esa.getClient();
        if (indexName == null) {
            indexName = esa.getIndexNameForType(indexType);
            log.info("Activate Elasticsearch backend for enrichment indexing: " + indexName);
            if (!client.indexExists(indexName)) {
                log.warn("Unknown index: " + indexName);
            }
        }
        return client;
    }

    @Override
    public void accept(EnrichmentMetadata enrichmentMetadata) {
        ESClient client = getClient();
        if (client != null) {
            List<String> jsonEntries = toEntries(enrichmentMetadata);
            if (!jsonEntries.isEmpty()) {
                append(client, jsonEntries);
            }
        }
    }

    public void append(ESClient client, List<String> jsonEntries) {
        BulkRequest bulkRequest = new BulkRequest();
        for (String json : jsonEntries) {
            IndexRequest request = new IndexRequest(indexName, indexType);
            request.source(json, XContentType.JSON);
            bulkRequest.add(request);
        }
        client.bulk(bulkRequest);
    }

    protected List<String> toEntries(EnrichmentMetadata metadata) {
        if (!metadata.getTags().isEmpty()) {
            return metadata.getTags().stream()
                           .map((AIMetadata.Tag tag) -> toTag(tag, metadata.getContext()))
                           .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    protected String toTag(AIMetadata.Tag tag, AIMetadata.Context context) {
        Map<String, Object> tagMap = new HashMap<>();
        tagMap.put("context", context);
        //Hmm, perhaps we can add a document change key or updated timestamp?
        tagMap.put("name", tag.name);
        tagMap.put("kind", tag.kind);
        tagMap.put("reference", tag.reference);
        tagMap.put("confidence", tag.confidence);

        if (tag.box != null) {
            Map<String, Object> boxMap = new HashMap<>();
            boxMap.put("width", tag.box.width);
            boxMap.put("height", tag.box.height);
            boxMap.put("left", tag.box.left);
            boxMap.put("top", tag.box.top);

            if (tag.box.centre != null) {
                Map<String, Object> pointMap = new HashMap<>();
                pointMap.put("x", tag.box.centre.getX());
                pointMap.put("y", tag.box.centre.getY());
                pointMap.put("box", tag.box.centre.getBox());
                boxMap.put("center", pointMap);
            }
            tagMap.put("box", boxMap);
        }
        try {
            return JacksonUtil.MAPPER.writeValueAsString(tagMap);
        } catch (JsonProcessingException e) {
            log.error("Unable to create tag for index: " + indexName, e);
        }

        return null;

    }
}
