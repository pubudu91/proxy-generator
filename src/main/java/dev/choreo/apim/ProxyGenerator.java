/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.choreo.apim;

import io.ballerina.projects.Document;
import io.ballerina.projects.Module;
import io.ballerina.projects.Project;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocumentChange;
import io.ballerina.tools.text.TextEdit;
import io.ballerina.tools.text.TextLine;
import io.ballerina.tools.text.TextRange;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static dev.choreo.apim.utils.ProjectAPIUtils.getDocument;
import static dev.choreo.apim.utils.ProjectAPIUtils.getLastLineInFile;

public class ProxyGenerator {

    private static final Path WORKING_DIR = Path.of(System.getProperty("user.dir"));

    public static void main(String[] args) throws IOException {
        // Needs to be the actual bal distribution. e.g., ballerina-2201.0.0-swan-lake/distributions/ballerina-2201.0.3
        System.setProperty("ballerina.home", args[0]);
        ProjectBuilder projectBuilder = new ProjectBuilder();
        Project project = projectBuilder
                .initProject(WORKING_DIR)
                .addOpenAPIDefinition(getOpenAPIZipInputStream(args[1]))
                .build();
        Module module = project.currentPackage().getDefaultModule();
        Document serviceDoc = getDocument(module, "proxy_service.bal");
        TextDocument txtDoc = serviceDoc.textDocument();

        TextDocumentChange docChange = getCodeSnippets(serviceDoc);
        txtDoc = txtDoc.apply(docChange);
        Document updatedServiceDoc = serviceDoc.modify().withContent(txtDoc.toString()).apply();

        SyntaxTreeTransformer transformer = new SyntaxTreeTransformer(getInflowTemplate(), getOutflowTemplate());
        docChange = transformer.modifyDoc(updatedServiceDoc, List.of("fooInMediate"), List.of("fooOutMediate"));
        txtDoc = txtDoc.apply(docChange);
        updatedServiceDoc = updatedServiceDoc.modify().withContent(txtDoc.toString()).apply();

        writeToFile(updatedServiceDoc, projectBuilder.getProjectPath());
    }

    private static InputStream getOpenAPIZipInputStream(String file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            if (entry.getName().endsWith("/Definitions/swagger.yaml")) {
                return zipFile.getInputStream(entry);
            }
        }

        throw new AssertionError("'/Definitions/swagger.yaml' file not found in the API artifact");
    }

    private static void writeToFile(Document doc, Path projectPath) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(
                Paths.get(projectPath.toString(), "_generated_" + doc.name()).toString()));
        writer.write(doc.textDocument().toString());
        writer.flush();
        writer.close();
    }

    private static TextDocumentChange getCodeSnippets(Document doc) {
        String content = readFile("code-snippets/boilerplate.bal");
        TextLine line = getLastLineInFile(doc.textDocument());
        TextRange textRange = TextRange.from(line.endOffset(), 0);
        TextEdit edit = TextEdit.from(textRange, content);
        return TextDocumentChange.from(new TextEdit[]{edit});
    }

    private static String getInflowTemplate() {
        return readFile("code-snippets/inflow_template.bal");
    }

    private static String getOutflowTemplate() {
        return readFile("code-snippets/outflow_template.bal");
    }

    private static String readFile(String filePath) {
        StringBuilder builder = new StringBuilder();
        InputStream fileStream = ProxyGenerator.class.getClassLoader().getResourceAsStream(filePath);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            fileStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return builder.toString();
    }
}
