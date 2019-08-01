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
package org.nuxeo.ai.comprehendmedical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import com.amazonaws.services.comprehendmedical.model.DetectEntitiesResult;
import com.amazonaws.services.comprehendmedical.model.DetectPHIResult;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.aws.aws-core"})
public class TestComprehendMedicalService {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testEntities() {
        AWS.assumeCredentials();
        EnrichmentService service = aiComponent.getEnrichmentService("aws.medicalEntities");
        assertNotNull(service);
        DetectEntitiesResult results = Framework.getService(ComprehendMedicalService.class)
                                                .detectEntities("cerealx 84 mg daily");
        assertNotNull(results);
        assertEquals(1, results.getEntities().size());

        BlobTextFromDocument textStream = new BlobTextFromDocument();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.addProperty("dc:title", "cerealx 84 mg daily");
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertEquals(1, result.getTags().size());
    }

    @Test
    public void testPHI() {
        AWS.assumeCredentials();
        EnrichmentService service = aiComponent.getEnrichmentService("aws.medicalPHI");
        assertNotNull(service);
        DetectPHIResult results = Framework.getService(ComprehendMedicalService.class)
                                           .detectPHI("Patient is John Smith, a 48 year old teacher and resident of Seattle, Washington.");
        assertNotNull(results);
        assertEquals(4, results.getEntities().size());

        BlobTextFromDocument textStream = new BlobTextFromDocument();
        textStream.setId("docId");
        textStream.setRepositoryName("test");
        textStream.addProperty("dc:title", "Patient is John Smith, a 48 year old teacher and resident of Seattle, Washington.");
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(textStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata result = metadataCollection.iterator().next();
        assertEquals(4, result.getTags().size());
    }

}
