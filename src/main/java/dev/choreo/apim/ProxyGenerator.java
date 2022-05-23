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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

public class ProxyGenerator {

    private static final Path WORKING_DIR = Paths.get("/Users/pubuduf/poc/mediation-gen");

    public static void main(String[] args) throws IOException {
        System.setProperty("ballerina.home",
                           "/Users/pubuduf/software/ballerina-2201.0.0-swan-lake/distributions/ballerina-2201.0.3");
        Project project = loadProject("pizza_shack");
        Module module = project.currentPackage().getDefaultModule();
        Document serviceDoc = getOpenAPIGeneratedService(module);
        TextDocument doc = serviceDoc.textDocument();
        TextLine line = getLastLineInFile(serviceDoc.textDocument());
        TextRange textRange = TextRange.from(line.endOffset(), 0);
        TextEdit edit = TextEdit.from(textRange, "\nhttp:Client backendEP = check new(\"http://localhost:9090\");");
        TextDocumentChange docChange = TextDocumentChange.from(List.of(edit).toArray(new TextEdit[0]));
        TextDocument modified = doc.apply(docChange);
        Document updatedServiceDoc = serviceDoc.modify().withContent(modified.toString()).apply();

        SyntaxTreeTransformer transformer = new SyntaxTreeTransformer();
        docChange = transformer.modifyDoc(updatedServiceDoc);
        modified = modified.apply(docChange);
        updatedServiceDoc = updatedServiceDoc.modify().withContent(modified.toString()).apply();

        writeToFile(updatedServiceDoc);
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
}
