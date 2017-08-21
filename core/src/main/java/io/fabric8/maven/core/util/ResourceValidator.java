/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.apache.commons.io.FileUtils;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import javax.validation.metadata.ConstraintDescriptor;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class ResourceValidator {

    private static final String KUBERNETES = "kubernetes";
    public static final String OPENSHIFT = "openshift";

    private File resources[];
    private String target = KUBERNETES;
    private final static String jsonSchemaPath = "https://raw.githubusercontent.com/garethr/%s-json-schema/master/%s-standalone/%s.json";

    public ResourceValidator(File file, String target) {
        this(file);
        this.target = target;
    }

    public ResourceValidator(File inputFile) {
        if(inputFile.isDirectory()) {
            resources = inputFile.listFiles();
        } else {
            resources = new File[]{inputFile};
        }
    }

    public int validate() throws ConstraintViolationException, IOException {
        for(File resource: resources) {
            JsonNode resourceNode = geFileContent(resource);
            JsonSchema schema = getJsonSchema(prepareSchemaUrl(resourceNode));
            Set<ValidationMessage> errors = schema.validate(resourceNode);
            processErrors(errors, resource);
        }

        return resources.length;
    }

    private void processErrors(Set<ValidationMessage> errors, File resource) {
        Set<ConstraintViolationImpl> constraintViolations = new HashSet<>();
        for (ValidationMessage errorMsg: errors) {
            constraintViolations.add(new ConstraintViolationImpl(errorMsg));
        }

        if(constraintViolations.size() > 0) {
            throw new ConstraintViolationException(getErrorMessage(resource, constraintViolations), constraintViolations);
        }
    }

    private String getErrorMessage(File resource, Set<ConstraintViolationImpl> violations) {
        StringBuilder validationError = new StringBuilder();
        validationError.append("Invalid Resource : ");
        validationError.append(resource.toString());

        for (ConstraintViolationImpl violation: violations) {
            validationError.append("\n");
            validationError.append(violation.toString());
        }

        return  validationError.toString();
    }

    private JsonSchema getJsonSchema(String schemaUrl) throws MalformedURLException {
        JsonSchemaFactory factory = new JsonSchemaFactory();
        return factory.getSchema(new URL(schemaUrl));
    }

    private String prepareSchemaUrl(JsonNode resourceNode) {
        return String.format(jsonSchemaPath, target, "master", resourceNode.get("kind").toString().toLowerCase()).replace("\"", "");
    }

    private JsonNode geFileContent(File file) throws IOException {
        InputStream resourceStream = null;
        try {
            resourceStream = new FileInputStream(file);
            ObjectMapper jsonMapper = new ObjectMapper(new YAMLFactory());
            return jsonMapper.readTree(resourceStream);
        } finally {
            resourceStream.close();
        }
    }

    private class ConstraintViolationImpl implements ConstraintViolation<ValidationMessage> {

        private ValidationMessage errorMsg;

        public ConstraintViolationImpl(ValidationMessage errorMsg) {
            this.errorMsg = errorMsg;
        }

        @Override
        public String getMessage() {
            return errorMsg.getMessage();
        }

        @Override
        public String getMessageTemplate() {
            return null;
        }

        @Override
        public ValidationMessage getRootBean() {
            return null;
        }

        @Override
        public Class<ValidationMessage> getRootBeanClass() {
            return null;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return new Object[0];
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public Path getPropertyPath() {
            return null;
        }

        @Override
        public Object getInvalidValue() {
            return null;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return null;
        }

        @Override
        public <U> U unwrap(Class<U> aClass) {
            return null;
        }

        @Override
        public String toString() {
            return "[message=" + getMessage().replaceFirst("[$]", "") +", violation type="+errorMsg.getType()+"]";
        }
    }
}
