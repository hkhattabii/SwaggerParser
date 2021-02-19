package Util;

import Component.Component;
import Configuration.Configuration;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import sun.security.krb5.Config;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {

    public static void writeFile(List<Configuration> configurations, String filename, Boolean single) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            if (single) {
                writer.writeValue(new File(filename), configurations);
            } else {
                for (Configuration configuration : configurations) {
                    Component component = (Component) configuration.getData();
                    writer.writeValue(new File(configuration.getData().getName() + "_v" + component.getVersion().getNumber() + ".json"), configuration);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> Set<T> mergeSet(Set<T> a, Set<T> b)
    {
        return new HashSet<T>() {{
            addAll(a);
            addAll(b);
        } };
    }
}
