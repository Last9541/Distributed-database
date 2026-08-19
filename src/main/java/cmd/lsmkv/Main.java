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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    //todo vrv ce se zvati init
    private static Lsm lsm;

    static ObjectMapper mapper = new ObjectMapper();

    //todo zbog ovoga nema locka za write, ali mozda ce trebati, ne zaboravi
    private static ExecutorService write= Executors.newSingleThreadExecutor();

    private static ExecutorService reader=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void main(String[] args) {
        Scanner scanner=new Scanner(System.in);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        System.out.println(System.getProperty("user.dir"));
        Map<String,String> arguments=new HashMap<>();
        boolean flag=true;
        while(flag) {
            args=scanner.next().split(" +");
            if (args.length == 0)
                throw new RuntimeException("ERROR");
            for (int i = 1; i < args.length - 1; i++) {
                if (args[i].startsWith("--") && !args[i + 1].startsWith("--"))
                    arguments.put(args[i].replace("-", ""), args[i + 1]);
            }
            switch (args[0].toLowerCase()) {
                case "init": {
                    //todo videti da li raditi ovo ili dodati throws za metodu
                    try {

                        if (!arguments.containsKey("config")) {
                            System.out.println("Default init");
                            lsm = new LsmImplementation();
                        } else {
                            System.out.println("Config loaded: " + arguments.get("config"));
                            lsm = new LsmImplementation(mapper.readValue(Path.of(arguments.get("config")).toFile(), Config.class));
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "put": {
                    if (lsm == null)
                        throw new RuntimeException("Moras da pozoves init");
                    if (arguments.containsKey("key") && arguments.containsKey("value")) {
                        write.submit(() -> lsm.put(arguments.get("key").getBytes(StandardCharsets.UTF_8), arguments.get("value").getBytes(StandardCharsets.UTF_8)));
                        //lsm.put(arguments.get("key").getBytes(StandardCharsets.UTF_8), arguments.get("value").getBytes(StandardCharsets.UTF_8));
                    } else {
                        throw new InvalidArgument();
                    }
                    break;
                }
                case "get": {
                    if (lsm == null)
                        throw new RuntimeException("Moras da pozoves init");
                    if (arguments.containsKey("key")) {
                        reader.submit(() -> System.out.println(Arrays.toString(lsm.get(arguments.get("key").getBytes(StandardCharsets.UTF_8)))));
                        //lsm.get(arguments.get("key").getBytes(StandardCharsets.UTF_8));
                    } else {
                        throw new InvalidArgument();
                    }
                    break;
                }
                case "del": {
                    if (lsm == null)
                        throw new RuntimeException("Moras da pozoves init");
                    if (arguments.containsKey("key")) {
                        write.submit(() -> lsm.delete(arguments.get("key").getBytes(StandardCharsets.UTF_8)));
                        //lsm.delete(arguments.get("key").getBytes(StandardCharsets.UTF_8));
                    } else {
                        throw new InvalidArgument();
                    }
                    break;
                }
                //TODO uradi ovo
                case "stats": {
                    if (lsm == null)
                        throw new RuntimeException("Moras da pozoves init");
                    System.out.println(lsm.stats());
                    break;
                }
                //todo proveri da li close radi lepo
                case "close": {
                    if (lsm == null)
                        throw new RuntimeException("Moras da pozoves init");
                    lsm.close();
                    flag=false;
                    break;
                }
            }
            arguments.clear();
        }
    }
}
