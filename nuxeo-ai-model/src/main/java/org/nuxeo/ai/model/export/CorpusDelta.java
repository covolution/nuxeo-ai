/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.model.export;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpus Delta POJO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CorpusDelta {

    public static final String MINIMUM_DOCS_PROP = "nuxeo.ai.export.min.docs";

    public static final String CORPORA_ID_PARAM = "corporaId";

    private static final int MINIMUM_DOCS_DEFAULT = 10;

    protected String corporaId;

    protected String query;

    protected List<Map<String, Object>> inputs;

    protected List<Map<String, Object>> outputs;

    protected Calendar end;

    protected int minSize = 0;

    public CorpusDelta(@JsonProperty(CORPORA_ID_PARAM) String corporaId, @JsonProperty("query") String query,
            @JsonProperty("inputs") List<Map<String, Object>> inputs,
            @JsonProperty("outputs") List<Map<String, Object>> outputs, @JsonProperty("end") GregorianCalendar end) {
        this.corporaId = corporaId;
        this.query = query;
        this.inputs = inputs;
        this.outputs = outputs;
        this.end = end;
    }

    public String getCorporaId() {
        return corporaId;
    }

    public String getQuery() {
        return query;
    }

    public Set<PropertyType> getInputs() {
        return inputs.stream()
                     .map(field -> new PropertyType((String) field.get("name"), (String) field.get("type")))
                     .collect(Collectors.toSet());
    }

    public Set<PropertyType> getOutputs() {
        return outputs.stream()
                      .map(field -> new PropertyType((String) field.get("name"), (String) field.get("type")))
                      .collect(Collectors.toSet());
    }

    public Calendar getEnd() {
        return end;
    }

    public int getMinSize() {
        String prop = Framework.getProperty(MINIMUM_DOCS_PROP, "0");
        int minDocs = Integer.parseInt(prop);
        if (minDocs == 0) {
            return minSize == 0 ? MINIMUM_DOCS_DEFAULT : minSize;
        } else {
            return minDocs;
        }
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public boolean isEmpty() {
        return StringUtils.isEmpty(query);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CorpusDelta that = (CorpusDelta) o;
        return minSize == that.minSize && Objects.equals(corporaId, that.corporaId) && Objects.equals(query, that.query)
                && Objects.equals(inputs, that.inputs) && Objects.equals(outputs, that.outputs) && Objects.equals(end,
                that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(corporaId, query, inputs, outputs, end, minSize);
    }
}
