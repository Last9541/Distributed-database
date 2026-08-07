package cmd.lsmkv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import internal.lsm.Config;
import internal.lsm.Global;
import internal.lsm.Lsm;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.implementation.LsmImplementation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {

    //todo vrv ce se zvati init
    private static Lsm lsm;

    static ObjectMapper mapper = new ObjectMapper();


    public static void main(String[] args) {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        System.out.println(System.getProperty("user.dir"));
        Map<String,String> arguments=new HashMap<>();
        if(args.length==0)
            throw new RuntimeException("ERROR");
        for(int i=1;i<args.length-1;i++)
        {
            if(args[i].startsWith("--") && !args[i+1].startsWith("--"))
                arguments.put(args[i].replace("-",""),args[i+1]);
        }
        switch (args[0].toLowerCase())
        {
            case "init":
            {
                //todo videti da li raditi ovo ili dodati throws za metodu
                try {

                    if(!arguments.containsKey("config")) {
                        System.out.println("Default init");
                        lsm=new LsmImplementation();
                    }
                    else {
                        System.out.println("Config loaded: " + arguments.get("config"));
                        lsm=new LsmImplementation(mapper.readValue(Path.of(arguments.get("config")).toFile(), Config.class));
                    }

                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case "put":
            {
                if(lsm==null)
                    throw new RuntimeException("Moras da pozoves init");
                if(arguments.containsKey("key") && arguments.containsKey("value"))
                    lsm.put(arguments.get("key").getBytes(StandardCharsets.UTF_8),arguments.get("value").getBytes(StandardCharsets.UTF_8));
                else {
                    throw new InvalidArgument();
                }
                break;
            }
            case "get":
            {
                if(lsm==null)
                    throw new RuntimeException("Moras da pozoves init");
                if(arguments.containsKey("key"))
                    lsm.get(arguments.get("key").getBytes(StandardCharsets.UTF_8));
                else {
                    throw new InvalidArgument();
                }
                break;
            }
            case "del":
            {
                if(lsm==null)
                    throw new RuntimeException("Moras da pozoves init");
                if(arguments.containsKey("key"))
                    lsm.delete(arguments.get("key").getBytes(StandardCharsets.UTF_8));
                else {
                    throw new InvalidArgument();
                }
                break;
            }
            case "stats":
            {
                if(lsm==null)
                    throw new RuntimeException("Moras da pozoves init");
                System.out.println("TODO: Prints the resolved config values and “engine status: stub”");
                break;
            }
            case "close":
            {
                if(lsm==null)
                    throw new RuntimeException("Moras da pozoves init");
                lsm.close();
                break;
            }

        }
    }
}
