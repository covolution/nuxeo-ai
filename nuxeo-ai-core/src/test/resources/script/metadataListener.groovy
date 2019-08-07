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
package script

import org.nuxeo.ai.enrichment.EnrichmentMetadata

EnrichmentMetadata metadata = context.properties["enrichmentMetadata"]
def doc = context.sourceDocument
doc.setPropertyValue("dc:title", metadata.labels.collect({ it.values.collect { it.name }.join(" ") }).first())
context.coreSession.saveDocument(doc)
