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
package org.nuxeo.ai.cloud;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_CORPORA_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_DOCUMENTS_COUNT;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_EVALUATION_DATA;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_INPUTS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_OUTPUTS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_QUERY;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_SPLIT;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_STATS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TRAINING_DATA;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.auth.JWTAuthenticator.LEEWAY_10_MIN;
import static org.nuxeo.ai.cloud.NuxeoCloudClient.API_AI;
import static org.nuxeo.ai.model.serving.TestModelServing.createTestBlob;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.api.CoreInstance.getCoreSession;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.keystore.JWKService;
import org.nuxeo.ai.keystore.KeyPairContainer;
import org.nuxeo.ai.model.export.CorpusDelta;
import org.nuxeo.ai.model.serving.FetchInsightURI;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.impl.PublicClaims;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, AutomationFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-model" })
@Deploy({ "org.nuxeo.ai.ai-model:OSGI-INF/disable-ai-listeners.xml" })
public class CloudClientTest {

    private static final ObjectMapper LOCAL_OM = new ObjectMapper();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Inject
    protected BlobManager manager;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected CloudClient client;

    @Inject
    protected AutomationService automationService;

    @Test
    public void testClient() {
        assertFalse(client.isAvailable(session));
        // Doesn't do anything because its not configured.
        DocumentModel not_used = session.createDocumentModel("/", "not_used", DATASET_EXPORT_TYPE);
        assertNull(client.uploadedDataset(not_used));

        assertThat(((NuxeoCloudClient) client).configureClient(session, new CloudConfigDescriptor())
                                              .orElse(null)).isNull();
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testConfiguredSuccess() throws IOException {
        assertNotNull(client.uploadedDataset(testDocument()));
        String secret = client.getClient(session).get().getConfiguration().getAuthentication().getSecret();
        assertThat(secret).isNotEmpty();

        DecodedJWT decode = JWT.decode(secret);
        String kid = decode.getKeyId();
        assertThat(kid).isNotEmpty();

        JWKService jwk = Framework.getService(JWKService.class);
        KeyPairContainer container = jwk.get(kid);
        assertThat(container).isNotNull();

        JWTVerifier verifier = JWT.require(Algorithm.RSA256(container.getPublicKey(), null))
                                  .withIssuer("mockTestProject")
                                  .withAudience("nuxeo")
                                  .withSubject("Administrator")
                                  .withClaim(PublicClaims.NOT_BEFORE, Instant.now().toEpochMilli() + 3000)
                                  .acceptIssuedAt(LEEWAY_10_MIN)
                                  .acceptExpiresAt(10)
                                  .build();

        verifier.verify(secret);
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testReconnection() throws IOException {
        // TODO: test it
        // ((NuxeoCloudClient) client).client = null;

        assertNotNull(client.uploadedDataset(testDocument()));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-bad-test.xml")
    public void testConfiguredFails() throws IOException {
        assertNull(client.uploadedDataset(testDocument()));
    }

    protected DocumentModel testDocument() throws IOException {
        ManagedBlob managedBlob = createTestBlob(manager);

        // Create a document
        DocumentModel doc = session.createDocumentModel("/", "corpora", DATASET_EXPORT_TYPE);
        doc = session.createDocument(doc);
        String jobId = "testing1";
        String query = "SELECT * FROM Document WHERE ecm:primaryType = 'Note'";
        Long split = 77L;
        doc.setPropertyValue(DATASET_EXPORT_CORPORA_ID, "1029148b-1313-410d-8351-a04e8324822c");
        doc.setPropertyValue(DATASET_EXPORT_MODEL_ID, "8476148b-1313-410d-8351-a04e8324822c");
        doc.setPropertyValue(DATASET_EXPORT_JOB_ID, jobId);
        doc.setPropertyValue(DATASET_EXPORT_SPLIT, split);
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "dc:title");
        fields.put("type", "txt");
        doc.setPropertyValue(DATASET_EXPORT_INPUTS, (Serializable) Collections.singletonList(fields));
        fields.put("name", "dc:creator");
        doc.setPropertyValue(DATASET_EXPORT_OUTPUTS, (Serializable) Collections.singletonList(fields));
        doc.setPropertyValue(DATASET_EXPORT_QUERY, query);
        doc.setPropertyValue(DATASET_EXPORT_TRAINING_DATA, (Serializable) managedBlob);
        doc.setPropertyValue(DATASET_EXPORT_EVALUATION_DATA, (Serializable) managedBlob);
        doc.setPropertyValue(DATASET_EXPORT_STATS, (Serializable) managedBlob);
        Long documentsCountValue = 1000L;
        doc.setPropertyValue(DATASET_EXPORT_DOCUMENTS_COUNT, documentsCountValue);
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        return doc;
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testGetPut() {
        String result = client.getClient(session)
                              .get()
                              .get(API_AI + client.byProjectId("/dev/models?enrichers.document=children"),
                                      response -> response.isSuccessful() ? response.body().string() : null);

        String result2 = client.getByProject(session, "/dev/models?enrichers.document=children",
                response -> response.isSuccessful() ? response.body().string() : null);

        assertEquals(result, result2);

        String putBody = "could be anything";
        String resBody = client.getClient(session)
                               .get()
                               .put(client.byProjectId("/dev/models"), putBody,
                                       response -> response.isSuccessful() ? response.body().string() : null);
        assertTrue(resBody.contains(putBody));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldGetModelsFromCloud() throws IOException, ExecutionException {
        JSONBlob models = client.getAllModels(session);
        @SuppressWarnings("unchecked")
        Map<String, Serializable> map = LOCAL_OM.readValue(models.getString(), Map.class);
        assertThat(map).containsKey("entries");
        assertThat(map.get("entries")).asList().isNotEmpty();
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-bad-test.xml")
    public void shouldGetEmptyModelsOnWrongDatasource() throws IOException, ExecutionException {
        JSONBlob models = client.getModelsByDatasource(session);

        @SuppressWarnings("unchecked")
        Map<String, Serializable> map = LOCAL_OM.readValue(models.getString(), Map.class);
        assertThat(map).containsKey("entries");
        assertThat(map.get("entries")).asList().isEmpty();
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldRetrieveDeltaCorpus() throws IOException, ExecutionException {
        DocumentModel datasetDoc = testDocument();
        DatasetExport dataset = datasetDoc.getAdapter(DatasetExport.class);
        JSONBlob corpusDelta = client.getCorpusDelta(session, dataset.getModelId());
        assertNotNull(corpusDelta);

        CorpusDelta delta = MAPPER.readValue(corpusDelta.getStream(), CorpusDelta.class);
        assertNotNull(delta);
        assertThat(delta.getInputs().stream().map(PropertyType::getName).collect(Collectors.toList())).hasSize(1)
                                                                                                      .contains(
                                                                                                              "file:content");
        assertThat(delta.getOutputs().stream().map(PropertyType::getName).collect(Collectors.toList())).hasSize(1)
                                                                                                       .contains(
                                                                                                               "dc:title");
    }

    @Test
    public void testTitle() {
        NuxeoCloudClient nuxClient = (NuxeoCloudClient) client;
        assertEquals("2 features, 34 Training, 56 Evaluation, Export id xyz", nuxClient.makeTitle(34, 56, "xyz", 2));
        assertEquals("0 features, 100 Training, 206 Evaluation, Export id xyzx",
                nuxClient.makeTitle(100, 206, "xyzx", 0));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void iCanRetrieveCloudConfigURI() throws OperationException {
        OperationContext ctx = new OperationContext();
        CoreSession session = getCoreSession("test");
        ctx.setCoreSession(session);
        JSONBlob uri = (JSONBlob) automationService.run(ctx, FetchInsightURI.ID);
        assertThat(uri.getString()).contains("{\"aitoken\"");
    }
}
