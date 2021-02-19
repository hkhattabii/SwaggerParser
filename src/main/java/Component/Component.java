package Component;

import Configuration.Data;
import Configuration.Parameter;
import Configuration.Version;

import java.util.List;


@lombok.Data
public class Component extends Data {
    private Version version;

    public Component(String name, String description, String type, Version version, List<Parameter> parameters) {
        super(name, description, type, parameters);
        this.version = version;
    }
}
