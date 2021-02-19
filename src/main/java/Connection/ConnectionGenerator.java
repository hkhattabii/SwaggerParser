package Connection;


import Configuration.*;
import Util.Utils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class ConnectionGenerator {

    private List<Configuration> configurations;

    public void generate(Boolean single) {
        Utils.writeFile(configurations, "Connections.json", single);
    }

    public ConnectionGenerator parse(OpenAPI openAPI, String[] fakeEnvs) {
        String description = openAPI.getInfo().getDescription();
        List<Parameter> hosts = getHosts(openAPI.getServers());
        configurations = hosts.stream().map(host -> {
            URL url = toUrlModel(host.getValue());
            assert url != null;
            Parameter port = new Parameter("port", getPort((url)));
            Parameter protocol = new Parameter("tls", getProtocol(url));
            Parameter baseUrl = new Parameter("baseUrl", getBaseUrl(url));
            String name = openAPI.getInfo().getTitle() + (protocol.getValue() == "Y" ? " HTTPS" : " HTTP");
            String env = fakeEnvs[hosts.indexOf(host)];
            Connection connection = new Connection(name, description, "http", env, Arrays.asList(host, port, protocol, baseUrl));
            return new Configuration("Connection", connection);
        }).collect(Collectors.toList());
        return this;
    }


    public List<Parameter> getHosts(List<Server> servers) {
        return servers.stream().map(server -> new Parameter("host", server.getUrl())).collect(Collectors.toList());
    }
    public static String getPort(URL url)  {
        int port = url.getPort();
        if (port == -1 ) return "";
        return String.valueOf(port);
    }

    public String getProtocol(URL url) {
        String protocol = url.getProtocol();
        return protocol.equals("http") ? "N" : "Y";
    }

    public String getBaseUrl(URL url) {
        return url.getFile();
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
