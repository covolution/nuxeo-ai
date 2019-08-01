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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

import net.jodah.failsafe.RetryPolicy;

/**
 * An enrichment service for entities analysis
 */
public abstract class AbstractMedicalEnrichmentService extends AbstractEnrichmentService implements EnrichmentCachable {

    public static final String DEFAULT_CONFIDENCE = "0.7";

    protected float minConfidence;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, String> prop : blobTextFromDoc.getProperties().entrySet()) {
                enriched.addAll(process(blobTextFromDoc, prop.getKey(), prop.getValue()));
            }
            return enriched;
        });
    }

    protected abstract Collection<EnrichmentMetadata> process(BlobTextFromDocument blobTextFromDoc,
                                                              String propName,
                                                              String propValue);

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
