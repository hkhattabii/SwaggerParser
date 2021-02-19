import Component.ComponentGenerator;
import Connection.ConnectionGenerator;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.File;
import java.util.Objects;


public class SwaggerParser {

    public static String[] fakeEnvs = new String[] {"TST", "PRD", "ENVV"};

    public static void main(String[] args) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File docFile = new File(Objects.requireNonNull(classLoader.getResource("doc.yaml")).getFile());
        SwaggerParseResult result = new OpenAPIParser().readLocation(String.valueOf(docFile), null, null);
        OpenAPI openAPI = result.getOpenAPI();

        if (result.getMessages() != null) result.getMessages().forEach(System.err::println); // validation errors and warnings

        if (openAPI != null) {
            ComponentGenerator componentGenerator = new ComponentGenerator();
            componentGenerator.parse(openAPI).generate("component_configs.json", false);
            ConnectionGenerator connectionGenerator = new ConnectionGenerator();
            connectionGenerator.parse(openAPI, fakeEnvs).generate(true);

        }
    }
}
