package Component;

import Configuration.*;
import Util.Utils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.*;
import java.util.stream.Collectors;

public class ComponentGenerator {

    private List<Configuration> configurations;
    private String versionNumber;
    private String connectionName;

    public void generate(String fileName, Boolean single) {
        Utils.writeFile(configurations, fileName, single);
    }

    public ComponentGenerator parse(OpenAPI openAPI) {
        configurations = new ArrayList<>();
        versionNumber = openAPI.getInfo().getVersion();
        connectionName = openAPI.getInfo().getTitle();
        Paths paths = openAPI.getPaths();

        for (String pathName : paths.keySet()) {
            PathItem path = paths.get(pathName);
            initComponents(path.getPut(), "PUT", pathName);
        }

        return this;
    }

    private void initComponents(Operation operation, String operationName, String pathName) {
        if (operation != null) {

            Set<String> requestContents = new HashSet<>();
            Set<String> responseContents = new HashSet<>();
            Set<String> securities = new HashSet<>();
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                requestContents = operation.getRequestBody().getContent().keySet();
                if (requestContents == null) {
                    responseContents = new HashSet<>();
                }
            }
            if (operation.getResponses() != null) {
                ApiResponses responses = operation.getResponses();
                for (String key : responses.keySet()) {
                    if (responses.get(key).getContent() != null) {
                        responseContents = responses.get(key).getContent().keySet();
                        if (responseContents == null) {
                            responseContents = new HashSet<>();
                        }
                    }
                }
            }
            if (operation.getParameters() != null) {
                securities = operation.getParameters().stream().map(parameter -> {
                    if (parameter.getIn() != null && parameter.getIn().equals("header")) {
                        return parameter.getName();
                    }
                    return null;
                }).filter(security -> security != null).collect(Collectors.toSet());
            }
            if (operation.getSecurity() != null) {
                if (securities != null) {
                    securities.addAll(operation.getSecurity().stream().map(securityRequirement -> (String) securityRequirement.keySet().toArray()[0]).collect(Collectors.toSet()));
                } else {
                    securities = operation.getSecurity().stream().map(securityRequirement -> (String) securityRequirement.keySet().toArray()[0]).collect(Collectors.toSet());
                }
            }


            try {

                List<String> names = generateName(securities, requestContents, responseContents);

                if (!names.isEmpty()) {
                    for (String name : names) {
                        createComponent(name, operation, operationName, pathName);
                    }
                } else {
                    createComponent(null, operation, operationName, pathName);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println(configurations);
    }
    
    private void createComponent(String name, Operation operation,String operationName, String pathName) {

        String componentName = "";
        if (name != null) {
            componentName = operation.getOperationId().concat(String.format(".%s", formatName(name)));
        } else {
            componentName = operation.getOperationId();
        }
        String componentType = "http.request";
        String componentDescription = operation.getDescription();
        Version componentVersion = new Version(versionNumber, componentDescription);
        Parameter endPoint = new Parameter("endpoint", pathName);
        Parameter type = new Parameter("type", operationName);
        Parameter connection = new Parameter("connection", connectionName);
        List<Parameter> params = new ArrayList();

        HashMap<String, String> headers = new HashMap<>();

        if (name != null) {
            try {
                List<String> parts = new LinkedList<>(Arrays.asList(name.split("\\.")));
                headers = createHeaders(parts.remove(0), parts, headers, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        for (String key : headers.keySet()) {
            params.add(new Parameter(key, headers.get(key)));
        }

        params.addAll(Arrays.asList(endPoint, type, connection));
        Collections.reverse(params);
        Component component = new Component(componentName, componentDescription, componentType, componentVersion, params);
        configurations.add(new Configuration("Component", component));
    }

    private HashMap<String, String> createHeaders(String name, List<String> names, HashMap<String, String> headers, Integer position) throws Exception {


        if (name.equals("_") && !names.isEmpty()) {
            return createHeaders(names.remove(0), names, headers, position);
        } else {
            if (name.contains("key")) {
                headers.put(String.format("header.%s", position), String.format("X-API-Key, %s","#api.key#"));
            } else if (name.contains("auth")) {
                headers.put(String.format("header.%s", position), String.format("Authorization,Bearer %s","#token#"));
            }
            else {
                List<String> parts = new LinkedList<>(Arrays.asList(name.split("\\/")));
                if (parts.get(0).equals("REQ")) {
                    headers.put(String.format("header.%s", position), String.format("Content-Type, %s", deserializeContentName(parts.get(1))));
                } else if (parts.get(0).equals("RES")) {
                    headers.put(String.format("header.%s", position), String.format("Accept, %s", deserializeContentName(parts.get(1))));
                }
            }

        }
        if (names.isEmpty()) {
            return headers;
        } else {
            return createHeaders(names.remove(0), names, headers, ++position);
        }
    }





    private List<String> generateName(Set<String> securities, Set<String> requestContents, Set<String> responseContents) throws Exception {
        List<String> names = new ArrayList<>();

        System.out.println("SECURITIES : " + securities);
        System.out.println("REQUEST CONTENTS : " + requestContents);
        System.out.println("RESPONSE CONTENTS : " + responseContents);
        String name = "";


        if (!securities.isEmpty()) {
            for (String security : securities) {
                if (!requestContents.isEmpty()) {
                    for (String requestContent : requestContents) {
                        if (!responseContents.isEmpty()) {
                            for (String responseContent : responseContents) {
                                name = security + ".REQ/" + serializeContentName(requestContent) + ".RES/" + serializeContentName(responseContent);
                                names.add(name);
                            }
                        } else {
                            for (String responseContent : responseContents) {
                                name = security + ".REQ/" + serializeContentName(requestContent);
                                names.add(name);
                            }
                        }

                    }
                } else if (!responseContents.isEmpty()) {
                    for (String responseContent : responseContents) {
                        name = security + ".RES/" + serializeContentName(responseContent);
                        names.add(name);
                    }
                } else {
                    names.add(security);
                }
            }
        } else if (!requestContents.isEmpty()) {
            for (String requestContent : requestContents) {
                if (!responseContents.isEmpty()) {
                    for (String responseContent : responseContents) {
                        name = "_" + ".REQ/" + serializeContentName(requestContent) + ".RES/" + serializeContentName(responseContent);
                        names.add(name);
                    }
                } else {
                    names.add("_" + ".REQ/" + serializeContentName(requestContent) + "._");
                }

            }
        } else if (!responseContents.isEmpty()) {
            for (String responseContent : responseContents) {
                name = "_._" + ".RES/" + serializeContentName(responseContent);
                names.add(name);
            }
        }

        System.out.println("NAMES : " + names);
        return names;
    }

    private String formatName(String name) {
        List<String> partsName = Arrays.asList(name.split("\\."));
        partsName = partsName.stream().map(part -> {
            if (part.contains("/")) {
                return formatContentName(part);
            }
            return part;
        }).collect(Collectors.toList());


        System.out.println(partsName);

        return String.join(".", partsName);
    }


    private String formatContentName(String contentName) {
        List<String> partContentName = new LinkedList<>(Arrays.asList(contentName.split("\\/")));
        if (partContentName.get(0).equals("REQ")) {
            return partContentName.get(1);
        } else if (partContentName.get(0).equals("RES")) {
            return partContentName.get(1);
        }

        return "ouoh";
    }

    private String serializeContentName(String contentName) throws Exception {
        switch (contentName) {
            case "application/json":
                return "JSON";
            case "application/xml":
                return "XML";
            case "application/x-www-form-urlencoded":
                return "FORM";
            default:
                throw new Exception("The content name doesn't exist");
        }
    }

    private String deserializeContentName(String contentName) throws Exception {
        switch (contentName) {

            case "JSON":
                return "application/json";
            case "XML":
                return "application/xml";
            case "FORM":
                return "application/x-www-form-urlencoded";
            default:
                throw new Exception("The content name doesn't exist");
        }
    }

}
