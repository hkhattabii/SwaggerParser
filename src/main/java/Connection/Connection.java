package Connection;

import Configuration.*;


import java.util.List;


@lombok.Data
public class Connection extends Data {
    private String environment;

    public Connection(String name, String description, String type, String environment, List<Parameter> parameters) {
        super(name, description, type, parameters);
        this.environment = environment;
    }



}

