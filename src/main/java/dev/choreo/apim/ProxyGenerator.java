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

import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Project;
import io.ballerina.projects.directory.ProjectLoader;
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
import java.util.Collection;
import java.util.List;

public class ProxyGenerator {

    private static final Path WORKING_DIR = Paths.get("/Users/pubuduf/poc/mediation-gen");

    public static void main(String[] args) throws IOException {
        setBallerinaHomeDistribution();
        Project project = loadProject("pizza_shack");
        Module module = project.currentPackage().getDefaultModule();
        Document serviceDoc = getOpenAPIGeneratedService(module);
        TextDocument txtDoc = serviceDoc.textDocument();

        TextDocumentChange docChange = getCodeSnippets(serviceDoc);
        txtDoc = txtDoc.apply(docChange);
        Document updatedServiceDoc = serviceDoc.modify().withContent(txtDoc.toString()).apply();

        SyntaxTreeTransformer transformer = new SyntaxTreeTransformer(getInflowTemplate(), getOutflowTemplate());
        docChange = transformer.modifyDoc(updatedServiceDoc, List.of("fooInMediate"), List.of("fooOutMediate"));
        txtDoc = txtDoc.apply(docChange);
        updatedServiceDoc = updatedServiceDoc.modify().withContent(txtDoc.toString()).apply();

        writeToFile(updatedServiceDoc);
    }

    private static void setBallerinaHomeDistribution() {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(System.getenv("SHELL"), "-c", "bal home");

        try {
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String balHome = reader.readLine();
            int exitVal = process.waitFor();

            if (exitVal != 0) {
                throw new RuntimeException("'bal home' command exited unexpectedly");
            }

            System.setProperty("ballerina.home", balHome);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeToFile(Document doc) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(
                Paths.get(WORKING_DIR.toString(), "pizza_shack", "_generated_" + doc.name()).toString()));
        writer.write(doc.textDocument().toString());
        writer.flush();
        writer.close();
    }

    private static Project loadProject(String sourceFilePath) {
        Path projectPath = WORKING_DIR.resolve(sourceFilePath);
        BuildOptions defaultOptions = BuildOptions.builder().setOffline(true).setDumpBirFile(true).build();
        return ProjectLoader.loadProject(projectPath, defaultOptions);
    }

    private static Document getOpenAPIGeneratedService(Module module) {
        Collection<DocumentId> documentIds = module.documentIds();

        for (DocumentId documentId : documentIds) {
            Document doc = module.document(documentId);
            if (doc.name().contains("service")) {
                return doc;
            }
        }

        throw new AssertionError("OpenAPI generated service not found");
    }

    private static TextLine getLastLineInFile(TextDocument doc) {
        int lastLine = 0;

        while (true) {
            try {
                doc.line(lastLine);
                lastLine++;
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }

        return doc.line(lastLine - 1);
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
