package Configuration;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;


@lombok.Data
@NoArgsConstructor
@AllArgsConstructor
public class Data {
    private String name;
    private String description;
    private String type;
    private List<Parameter> parameters;
}
