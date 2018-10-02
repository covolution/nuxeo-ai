package org.nuxeo.ai.rest;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.FILE_CONTENT;
import static org.nuxeo.ai.enrichment.TestDocMetadataService.TEST_PROPERTY;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.ai-core:OSGI-INF/enrichment-rest-custom.xml"})
public class TestRestCustomModel {

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected BlobManager manager;

    @Test
    @Ignore
    public void testCallService() throws IOException {
        assertNotNull(aiComponent);
        EnrichmentService service = aiComponent.getEnrichmentService("custom1");

        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(new File(getClass().getResource("/files/plane.jpg").getPath()), "image/jpeg");
        ManagedBlob plane = blob(blob, blobProvider.writeBlob(blob));
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument("docId", "default", "parent", "File", null);
        blobTextFromDoc.addBlob(FILE_CONTENT, plane);
        Collection<EnrichmentMetadata> results = service.enrich(blobTextFromDoc);
        assertNotNull("The api must successfully return a result", results);
        assertEquals("There must be 1 result", 1, results.size());
        EnrichmentMetadata metadata = results.iterator().next();
        assertEquals(1, metadata.getLabels().size());
        assertNotNull(metadata.getRawKey());

        TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentService(metadata.getServiceName());
        List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
        assertEquals(1, rawBlobs.size());
        String raw = rawBlobs.get(0).getString();
        JsonNode jsonTree = JacksonUtil.MAPPER.readTree(raw);
        assertNotNull(jsonTree);
        assertEquals("The custom model should return 5 results", 5, jsonTree.get("results").size());
        blobTextFromDoc.getBlobs().clear();
        blobTextFromDoc.addProperty(TEST_PROPERTY, "Great product");
        results = service.enrich(blobTextFromDoc);
        assertEquals("There must be 1 result", 1, results.size());
    }

    private ManagedBlob blob(Blob blob, String key) {
        return new BlobMetaImpl("test", blob.getMimeType(), key,
                                blob.getDigest(), blob.getEncoding(), blob.getLength()
        );
    }

}
