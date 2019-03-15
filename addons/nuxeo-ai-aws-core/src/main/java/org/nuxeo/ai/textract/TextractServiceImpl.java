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
package org.nuxeo.ai.textract;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.services.config.ConfigurationService;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.DetectDocumentTextRequest;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;

/**
 * Implementation of TextractService
 */
public class TextractServiceImpl extends DefaultComponent implements TextractService {

    public static final String PREPROCESS_TEXTRACT = "nuxeo.enrichment.aws.document.preprocess";

    private static final Logger log = LogManager.getLogger(TextractServiceImpl.class);

    protected volatile AmazonTextract client;

    protected boolean preprocess;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        preprocess = Framework.getService(ConfigurationService.class).isBooleanTrue(PREPROCESS_TEXTRACT);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        client = null;
    }

    /**
     * Get the AmazonTextractClient client
     */
    protected AmazonTextract getClient() {
        AmazonTextract localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    AmazonTextractClientBuilder builder =
                            AmazonTextractClientBuilder.standard()
                                                       .withCredentials(AWSHelper.getInstance()
                                                                                 .getCredentialsProvider())
                                                       .withRegion(AWSHelper.getInstance().getRegion());
                    client = localClient = builder.build();
                }
            }
        }
        return localClient;
    }

    @Override
    public DetectDocumentTextResult detectText(ManagedBlob blob) {
        if (log.isDebugEnabled()) {
            log.debug("Calling detectDocumentText for " + blob.getKey());
        }

        Document document = AWSHelper.getInstance().getDocument(blob);
        if (document != null) {
            DetectDocumentTextRequest request = new DetectDocumentTextRequest().withDocument(document);
            DetectDocumentTextResult result = getClient().detectDocumentText(request);

            if (log.isDebugEnabled()) {
                log.debug("DetectDocumentTextResult is " + result);
            }
            return result;
        }

        return null;
    }

    @Override
    public AnalyzeDocumentResult analyzeDocument(ManagedBlob blob, String... features) {
        if (log.isDebugEnabled()) {
            log.debug("Calling analyzeDocument for " + blob.getKey());
        }

        Document document = AWSHelper.getInstance().getDocument(blob);
        if (document != null) {
            AnalyzeDocumentRequest request = new AnalyzeDocumentRequest()
                    .withFeatureTypes(features)
                    .withDocument(document);

            AnalyzeDocumentResult result = getClient().analyzeDocument(request);
            if (log.isDebugEnabled()) {
                log.debug("AnalyzeDocumentResult is " + result);
            }
            return result;
        }
        return null;
    }

    @Override
    public boolean preprocessResults() {
        return preprocess;
    }

}
