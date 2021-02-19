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
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ComponentGenerator {

    private List<Configuration> configurations;
    private Map<String, SecurityScheme> securitySchemeMap;
    private String versionNumber;
    private String connectionName;

    public void generate(String fileName, Boolean single) {
        Utils.writeFile(configurations, fileName, single);
    }

    public ComponentGenerator parse(OpenAPI openAPI) {
        configurations = new ArrayList<>();
        securitySchemeMap = openAPI.getComponents().getSecuritySchemes();
        versionNumber = openAPI.getInfo().getVersion();
        connectionName = openAPI.getInfo().getTitle();



        Paths paths = openAPI.getPaths();

        for (String pathName : paths.keySet()) {
            PathItem path = paths.get(pathName);
            Map<PathItem.HttpMethod, Operation> operations = path.readOperationsMap();

            for (PathItem.HttpMethod httpMethod : operations.keySet()) {
                initComponents(operations.get(httpMethod), httpMethod, pathName);
            }

        }

        return this;
    }

    private void initComponents(Operation operation, PathItem.HttpMethod operationName, String pathName) {
        if (operation != null) {

            Set<String> securities = getSecurities(operation.getParameters(), operation.getSecurity());
            Set<String> requestContents = getRequestContents(operation.getRequestBody());
            Set<String> responseContents = getResponseContents(operation.getResponses());

            List<HashMap<String, String>>  names = generateName(securities, requestContents, responseContents);

            if (!names.isEmpty()) {
                for (HashMap<String, String> partNames : names) {
                    createComponent(partNames, operation, operationName, pathName);
                }
            } else {
                createComponent(new HashMap(), operation, operationName, pathName);
            }
        }

    }


    private Set<String> getSecurities(List<io.swagger.v3.oas.models.parameters.Parameter> parameters, List<SecurityRequirement> securitiesList) {
        Set<String> securities = new HashSet<>();
        if (parameters != null) {
            securities = parameters.stream().map(parameter -> {
                if (parameter.getIn() != null && securitySchemeMap.containsKey(parameter.getName())) {
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
                if (apiResponses.get(key).getContent() != null && (isGreenStatus(key) || key.equals("default"))) {
                    return apiResponses.get(key).getContent().keySet();
                }
            }
        }
        return new HashSet<>();
    }

    private void createComponent(HashMap<String, String> partNames, Operation operation,PathItem.HttpMethod operationName, String pathName) {
        String componentName = buildName(operation.getOperationId(), partNames);
        String componentType = "http.request";
        String componentDescription = operation.getDescription();
        Version componentVersion = new Version(versionNumber, componentDescription);
        List<Parameter> infos = getInfos(pathName, operationName);
        List<Parameter> queryParams = getQueryParams(operation.getParameters());
        List<Parameter> headers = getHeaders(partNames);
        List<Parameter> params = Stream.of(infos, queryParams, headers).flatMap(Collection::stream).collect(Collectors.toList());
        Component component = new Component(componentName, componentDescription, componentType, componentVersion, params);
        configurations.add(new Configuration("Component", component));
    }

    private List<Parameter> getInfos(String pathName, PathItem.HttpMethod operationName) {
        List<Parameter> infos = new ArrayList(){{
            add(new Parameter("endpoint", pathName));
            add(new Parameter("type", operationName.name()));
            add(new Parameter("connection", connectionName));
        }};
        return infos;
    }
    private List<Parameter> getQueryParams(List<io.swagger.v3.oas.models.parameters.Parameter> parameters) {
        List<Parameter> queryParams = new ArrayList();
        if (parameters != null) {
            parameters.forEach(parameter -> {
                if (parameter.getIn() != null && parameter.getIn().equals("query")) {
                    String parameterName = parameter.getName();
                    queryParams.add(new Parameter("queryparam.1", String.format("%s, #%s#", parameterName, parameterName)));
                }
            });
        }
        return queryParams;
    }


    private List<Parameter> getHeaders(HashMap<String, String> partNames) {
        int position = 0;
        List<Parameter> parameters = new ArrayList();
        List<String> headerTypes = new ArrayList(partNames.keySet());

        for (int i = 0; i < headerTypes.size(); i++) {
            String key = headerTypes.get(i);
            String value = partNames.get(key);
            if (value != null) {
                switch (key) {
                    case "security":
                        SecurityScheme.Type securityType = securitySchemeMap.get(value).getType();
                        if (securityType == SecurityScheme.Type.OAUTH2) {
                            parameters.add(new Parameter(String.format("header.%s", ++position), String.format("Authorization, Bearer #%s#",value)));
                        } else if (securityType == SecurityScheme.Type.APIKEY) {
                            parameters.add(new Parameter(String.format("header.%s",++position), String.format("X-API-KEY, #%s#", value)));
                        }
                        break;
                    case "request":
                        parameters.add(new Parameter(String.format("header.%s", ++position), String.format("Content-Type, %s", value)));
                        break;
                    case "response":
                        parameters.add(new Parameter(String.format("header.%s", ++position), String.format("Accept, %s", value)));
                        break;
                }
            }

        }

        return parameters;

    }
    private List<HashMap<String, String>>  generateName(Set<String> securities, Set<String> requestContents, Set<String> responseContents) {
        List<HashMap<String, String>> names = new ArrayList<>();


        if (!securities.isEmpty()) {
            for (String security : securities) {
                if (!requestContents.isEmpty()) {
                    for (String requestContent : requestContents) {
                        if (!responseContents.isEmpty()) {
                            for (String responseContent : responseContents) {
                                names.add(addPartName(security, requestContent, responseContent));
                            }
                        } else {
                            names.add(addPartName(security, requestContent, null));
                        }

                    }
                } else if (!responseContents.isEmpty()) {
                    for (String responseContent : responseContents) {
                        names.add(addPartName(security, null, responseContent));
                    }
                } else {
                    names.add(addPartName(security, null, null));
                }
            }
        } else if (!requestContents.isEmpty()) {
            for (String requestContent : requestContents) {
                if (!responseContents.isEmpty()) {
                    for (String responseContent : responseContents) {
                        names.add(addPartName(null, requestContent, responseContent));
                    }
                } else {
                    names.add(addPartName(null, requestContent, null));
                }

            }
        } else if (!responseContents.isEmpty()) {
            for (String responseContent : responseContents) {
                names.add(addPartName(null, null, responseContent));
            }
        }

        return names;
    }

    private LinkedHashMap<String, String> addPartName(String security, String requestContent, String responseContent) {
        return new LinkedHashMap<String, String>() {{
            put("security", security);
            put("request", requestContent);
            put("response", responseContent);
        }};
    }

    private Boolean isGreenStatus(String statusCode) {
        Pattern pattern = Pattern.compile("2[0-9][0-9]");
        return pattern.matcher(statusCode).matches();
    }

    private String buildName(String operationId, HashMap<String, String> partNames) {
        List<String> formatedPartNames = partNames.keySet().stream().map(key -> {
            if (partNames.get(key) == null) {
                return "_";
            }
            if (key.equals("request") || key.equals("response")) {
                try {
                    return serializeContentName(partNames.get(key));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return partNames.get(key);
        }).collect(Collectors.toList());


        return operationId.concat("." + listToString(formatedPartNames, "."));
    }


    private String listToString(List<String> list, String delimiter) {
        return String.join(delimiter, list);
    }

    private String serializeContentName(String contentName) throws Exception {
        switch (contentName) {
            case "application/json":
                return "JSON";
            case "application/xml":
                return "XML";
            case "application/x-www-form-urlencoded":
                return "FORM";
            case "application/octet-stream":
                return "OCTETSTRM";
            default:
                throw new Exception("The content name doesn't exist");
        }
    }


}
