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

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingProperties;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentService.MINIMUM_CONFIDENCE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.nuxeo.ai.comprehendmedical.ComprehendMedicalService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.runtime.api.Framework;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.comprehendmedical.model.DetectEntitiesResult;
import com.amazonaws.services.comprehendmedical.model.Entity;

import net.jodah.failsafe.RetryPolicy;

/**
 * An enrichment service for entities analysis
 */
public class MedicalEntitiesEnrichmentService extends AbstractEnrichmentService implements EnrichmentCachable {

    public static final String DEFAULT_CONFIDENCE = "0.7";

    protected float minConfidence;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {

        List<EnrichmentMetadata> enriched = new ArrayList<>();
        try {
            for (Map.Entry<String, String> prop : blobTextFromDoc.getProperties().entrySet()) {
                DetectEntitiesResult result = Framework.getService(ComprehendMedicalService.class)
                                                       .detectEntities(prop.getValue());
                if (result != null && !result.getEntities().isEmpty()) {
                    enriched.addAll(processResult(blobTextFromDoc, prop.getKey(), result));
                }
            }
            return enriched;
        } catch (AmazonServiceException e) {
            throw EnrichmentHelper.isFatal(e) ? new FatalEnrichmentError(e) : e;
        }
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
                                                           DetectEntitiesResult result) {
        List<AIMetadata.Tag> tags = result.getEntities()
                                          .stream()
                                          .map(this::newEntityTag)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());
        String raw = toJsonString(jg -> jg.writeObjectField("entities", result.getEntities()));
        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                                                 .withTags(tags)
                                                 .withRawKey(rawKey)
                                                 .withDocumentProperties(Collections.singleton(propName))
                                                 .build());
    }

    protected AIMetadata.Tag newEntityTag(Entity e) {
        if (e.getScore() > minConfidence) {
            List<AIMetadata.Label> features = new ArrayList<>();
            e.getAttributes().forEach(attribute ->
                                              features.add(new AIMetadata.Label(
                                                      attribute.getType() + "/" + attribute.getText(),
                                                      attribute.getScore())));
            e.getTraits().forEach(t -> features.add(new AIMetadata.Label(t.getName(), t.getScore())));
            return new AIMetadata.Tag(e.getText(), e.getCategory(), e.getType(), null, features, e.getScore());
        }
        return null;
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy()
                    .abortOn(throwable -> throwable.getMessage().contains("is not authorized to perform"));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingProperties(blobTextFromDoc, name);
    }

}
