package Connection;


import Configuration.*;
import Util.Utils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class ConnectionGenerator {

    private List<Configuration> configurations;

    public void generate(Boolean single) {
        Utils.writeFile(configurations, "Connections.json", single);
    }

    public ConnectionGenerator parse(OpenAPI openAPI, String[] fakeEnvs) {
        String name = openAPI.getInfo().getTitle();
        String description = openAPI.getInfo().getDescription();
        List<URL> adresses = getAdresses(openAPI.getServers());
        configurations = adresses.stream().map(address -> {
            String env = fakeEnvs[adresses.indexOf(address)];
            Parameter host = new Parameter("host", getHost(address));
            Parameter port = new Parameter("port", getPort((address)));
            Parameter protocol = new Parameter("tls", getProtocol(address));
            Parameter baseUrl = new Parameter("baseUrl", getBaseUrl(address));
            List<Parameter> parameters = new ArrayList(Arrays.asList(host, protocol, baseUrl));
            if (port.getValue() != null) {
                System.out.println("COUCOU : " + port);
                parameters.add(port);
            }
            Connection connection = new Connection(name, description, "http", env, parameters);
            return new Configuration("Connection", connection);
        }).collect(Collectors.toList());

        return this;
    }


    public List<URL> getAdresses(List<Server> servers) {
        return servers.stream().map(server -> toUrlModel(server.getUrl())).filter(url -> url != null).collect(Collectors.toList());
    }

    public String getHost(URL url) {
        return url.getProtocol() + "://" + url.getHost();
    }


    public String getPort(URL url)  {
        int port = url.getPort();
        if (port == -1 ) return null;
        return String.valueOf(port);
    }

    public String getProtocol(URL url) {
        String protocol = url.getProtocol();
        return protocol.equals("http") ? "N" : "Y";
    }

    public String getBaseUrl(URL url) {
        return url.getPath();
    }

    public URL toUrlModel(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

}
