package Component;

import Configuration.*;
import Util.Utils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;

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
            initComponents(path.getGet(), "GET", pathName);
            initComponents(path.getPost(), "POST", pathName);
            initComponents(path.getPut(), "PUT", pathName);
            initComponents(path.getDelete(), "DELETE", pathName);
        }

        return this;
    }

    private void initComponents(Operation operation, String operationName, String pathName) {
        if (operation != null) {

            Set<String> securities = getSecurities(operation.getParameters(), operation.getSecurity());
            Set<String> requestContents = getRequestContents(operation.getRequestBody());
            Set<String> responseContents = getResponseContents(operation.getResponses());

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


    private Set<String> getSecurities(List<io.swagger.v3.oas.models.parameters.Parameter> parameters, List<SecurityRequirement> securitiesList) {
        Set<String> securities = new HashSet<>();
        if (parameters != null) {
            securities = parameters.stream().map(parameter -> {
                if (parameter.getIn() != null && parameter.getIn().equals("header")) {
                    return parameter.getName();
                }
                return null;
            }).filter(value -> value != null).collect(Collectors.toSet());
        }
        if (securitiesList != null) {
            if (securities != null) {
                securities.addAll(securitiesList.stream().map(securityRequirement -> (String) securityRequirement.keySet().toArray()[0]).collect(Collectors.toSet()));
            }
        }
        return securities;
    }

    private Set<String> getRequestContents(RequestBody requestBody) {
        if (requestBody != null && requestBody.getContent() != null) {
            return requestBody.getContent().keySet();
        }
        return new HashSet<>();
    }

    private Set<String> getResponseContents(ApiResponses apiResponses) {
        if (apiResponses!= null) {
            for (String key : apiResponses.keySet()) {
                if (apiResponses.get(key).getContent() != null && (key.equals("200") || key.equals("default"))) {
                    return apiResponses.get(key).getContent().keySet();
                }
            }
        }
        return new HashSet<>();
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

        HashMap<String, String> additionnalParams = getAdditionalParams(operation.getParameters());


        if (name != null) {
            try {
                List<String> parts = new LinkedList<>(Arrays.asList(name.split("\\.")));
                additionnalParams.putAll(createHeaders(parts.remove(0), parts, additionnalParams, 1));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        for (String key : additionnalParams.keySet()) {
            params.add(new Parameter(key, additionnalParams.get(key)));
        }

        params.addAll(Arrays.asList(endPoint, type, connection));
        Component component = new Component(componentName, componentDescription, componentType, componentVersion, params);
        configurations.add(new Configuration("Component", component));
    }

    private HashMap<String, String> getAdditionalParams(List<io.swagger.v3.oas.models.parameters.Parameter> parameters) {
        HashMap<String, String> additionalParams = new HashMap<>();
        if (parameters != null) {
            parameters.forEach(parameter -> {
                if (parameter.getIn() != null && parameter.getIn().equals("query")) {
                    String parameterName = parameter.getName();
                    additionalParams.put("queryparam.1", String.format("%s, #%s#", parameterName, parameterName));
                }
            });
        }
        return additionalParams;
    }

    private HashMap<String, String> createHeaders(String name, List<String> names, HashMap<String, String> additionnalParams, Integer position) throws Exception {

        if (name.equals("_") && !names.isEmpty()) {
            return createHeaders(names.remove(0), names, additionnalParams, position);
        } else {
            if (name.contains("key")) {
                additionnalParams.put(String.format("header.%s", position), String.format("X-API-Key, %s","#api.key#"));
            } else if (name.contains("auth")) {
                additionnalParams.put(String.format("header.%s", position), String.format("Authorization,Bearer %s","#token#"));
            }
            else {
                List<String> parts = new LinkedList<>(Arrays.asList(name.split("\\/")));
                if (parts.get(0).equals("REQ")) {
                    additionnalParams.put(String.format("header.%s", position), String.format("Content-Type, %s", deserializeContentName(parts.get(1))));
                } else if (parts.get(0).equals("RES")) {
                    additionnalParams.put(String.format("header.%s", position), String.format("Accept, %s", deserializeContentName(parts.get(1))));
                }
            }
        }
        if (names.isEmpty()) {
            return additionnalParams;
        } else {
            return createHeaders(names.remove(0), names, additionnalParams, ++position);
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
                            name = security + ".REQ/" + serializeContentName(requestContent);
                            names.add(name);
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

        return "";
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
