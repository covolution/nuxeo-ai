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
package org.nuxeo.ai.enrichment;

import java.util.Collection;

/**
 * Additional information about an EnrichmentProvider and what it supports.
 *
 * @see EnrichmentProvider
 */
public interface EnrichmentSupport {

    /**
     * Initialize the provider based on the descriptor
     */
    void init(EnrichmentDescriptor descriptor);

    /**
     * Adds supported mimetypes
     */
    void addMimeTypes(Collection<String> mimeTypes);

    /**
     * Does the provider support the mimeType.
     * If the provider doesn't declare what it supports then this method will return true.
     */
    boolean supportsMimeType(String mimeType);

    /**
     * Does the provider support a file of this size (in bytes).
     */
    boolean supportsSize(long size);
}
