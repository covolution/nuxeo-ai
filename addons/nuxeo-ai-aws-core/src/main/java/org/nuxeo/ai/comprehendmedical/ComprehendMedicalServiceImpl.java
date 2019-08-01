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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import com.amazonaws.services.comprehendmedical.AWSComprehendMedical;
import com.amazonaws.services.comprehendmedical.AWSComprehendMedicalClientBuilder;
import com.amazonaws.services.comprehendmedical.model.DetectEntitiesRequest;
import com.amazonaws.services.comprehendmedical.model.DetectEntitiesResult;
import com.amazonaws.services.comprehendmedical.model.DetectPHIRequest;
import com.amazonaws.services.comprehendmedical.model.DetectPHIResult;

/**
 * Calls AWS Comprehend Medical apis
 */
public class ComprehendMedicalServiceImpl extends DefaultComponent implements ComprehendMedicalService {

    private static final Log log = LogFactory.getLog(ComprehendMedicalServiceImpl.class);

    protected volatile AWSComprehendMedical client;

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        client = null;
    }

    /**
     * Get the AWSComprehendMedical client
     */
    protected AWSComprehendMedical getClient() {
        AWSComprehendMedical localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    AWSComprehendMedicalClientBuilder builder =
                            AWSComprehendMedicalClientBuilder.standard()
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
    public DetectEntitiesResult detectEntities(String text) {

        if (log.isDebugEnabled()) {
            log.debug("Calling DetectEntities for " + text);
        }

        DetectEntitiesRequest detectEntitiesRequest = new DetectEntitiesRequest().withText(text);
        DetectEntitiesResult detectEntitiesResult = getClient().detectEntities(detectEntitiesRequest);

        if (log.isDebugEnabled()) {
            log.debug("DetectEntitiesResult is " + detectEntitiesResult);
        }
        return detectEntitiesResult;
    }

    @Override
    public DetectPHIResult detectPHI(String text) {

        if (log.isDebugEnabled()) {
            log.debug("Calling DetectPHI for " + text);
        }

        DetectPHIRequest detectRequest = new DetectPHIRequest().withText(text);
        DetectPHIResult detectResult = getClient().detectPHI(detectRequest);

        if (log.isDebugEnabled()) {
            log.debug("DetectPHIResult is " + detectResult);
        }
        return detectResult;
    }
}
