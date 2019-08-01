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

import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.nuxeo.ai.comprehendmedical.ComprehendMedicalService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.runtime.api.Framework;
import com.amazonaws.services.comprehendmedical.model.DetectPHIResult;
import com.amazonaws.services.comprehendmedical.model.Entity;

/**
 * An enrichment service for personal health information analysis
 */
public class MedicalPHIEnrichmentService extends AbstractMedicalEnrichmentService implements EnrichmentCachable {

    @Override
    protected Collection<EnrichmentMetadata> process(BlobTextFromDocument blobTextFromDoc, String propName,
                                                     String propValue) {
        DetectPHIResult result = Framework.getService(ComprehendMedicalService.class).detectPHI(propValue);
        if (result != null && !result.getEntities().isEmpty()) {
            return processResult(blobTextFromDoc, propName, result);
        }
        return Collections.emptyList();
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
                                                           DetectPHIResult result) {
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
            if (e.getAttributes() != null) {
                e.getAttributes()
                 .forEach(attr -> features.add(new AIMetadata.Label(attr.getType() + "/" + attr.getText(),
                                                                    attr.getScore())));
            }
            if (e.getTraits() != null) {
                e.getTraits().forEach(t -> features.add(new AIMetadata.Label(t.getName(), t.getScore())));
            }
            return new AIMetadata.Tag(e.getText(), e.getType(), e.getCategory(), null, features, e.getScore());
        }
        return null;
    }

}
