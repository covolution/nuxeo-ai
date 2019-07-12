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
package org.nuxeo.ai.auto;

import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Configuration done on XML as an extension
 * A global threshold for all properties
 * We can define different thresholds to different properties
 */
public interface ThresholdService {

    float getAutoFillThreshold(DocumentModel doc, String xpath);
    float getAutoCorrectThreshold(DocumentModel doc, String xpath);

    //DocumentType or facet
    void setThreshold(String documentType, String xpath, float threshold);
}
