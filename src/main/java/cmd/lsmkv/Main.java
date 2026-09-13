package cmd.lsmkv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import internal.lsm.Config;
import internal.lsm.Global;
import internal.lsm.Lsm;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.implementation.Compaction;
import internal.lsm.implementation.LsmImplementation;
import internal.lsm.implementation.SSTable;

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
import java.util.concurrent.ScheduledExecutorService;

public class Main {


    public static ExecutorService ssTableWriter=Executors.newSingleThreadExecutor();

    private static Lsm lsm=new LsmImplementation();

    public static ObjectMapper mapper = new ObjectMapper();

    private static Config instance=new Config();

    //todo zbog ovoga nema locka za write, ali mozda ce trebati, ne zaboravi
    public static ExecutorService write= Executors.newSingleThreadExecutor();

    //private static ExecutorService reader=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static ScheduledExecutorService compactionWorker=Executors.newSingleThreadScheduledExecutor();

    public static ExecutorService compactionLoop=Executors.newSingleThreadExecutor();


    public static Compaction compaction=new Compaction((SSTable) lsm);

    public static RuntimeException exception;


    static {
        mapper.registerModule(new JavaTimeModule());
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public static void main(String[] args) {
        Scanner scanner=new Scanner(System.in);

        System.out.println(System.getProperty("user.dir"));
        Map<String,String> arguments=new HashMap<>();
        boolean flag=true;
        while(flag) {
            arguments.clear();
            if(exception!=null) {
                System.out.println(exception.getMessage());
            }
            args=Parser.parse(scanner.nextLine().toCharArray());
            if (args.length == 0)
                throw new RuntimeException("ERROR");
            for (int i = 2; i < args.length - 1; i++) {
                if (args[i].startsWith("--") && !args[i + 1].startsWith("--"))
                    arguments.put(args[i].replace("-", ""), args[i + 1]);
            }
            if(args.length<2 && !args[0].equals("lsmvk"))
                continue;
            switch (args[1].toLowerCase()) {
                case "init": {
                    //todo videti da li raditi ovo ili dodati throws za metodu
                    try {
                        Config config=null;
                        if (arguments.containsKey("config"))
                        {
                            System.out.println("Config loaded: " + arguments.get("config"));
                            config=mapper.readValue(Path.of(arguments.get("config")).toFile(), Config.class);
                        }
                        else {
                            System.out.println("Default init");
                            Path path = Path.of("config/default.json");
                            if(Files.notExists(path))
                                config=instance;
                            else
                                config=mapper.readValue(path.toFile(), Config.class);
                        }
                        try {
                            lsm.init(config);
                        }
                        catch (RuntimeException e)
                        {
                            exception=e;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "put": {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    if (arguments.containsKey("key") && arguments.containsKey("value")) {
                        byte[] key=arguments.get("key").getBytes(StandardCharsets.UTF_8);
                        byte[] value=arguments.get("value").getBytes(StandardCharsets.UTF_8);
                        write.submit(() ->
                        {
                            try {
                                lsm.put(key,value);
                            }
                            catch (RuntimeException e)
                            {
                                exception=e;
                            }
                        });
                        //lsm.put(arguments.get("key").getBytes(StandardCharsets.UTF_8), arguments.get("value").getBytes(StandardCharsets.UTF_8));
                    } else {
                        exception=new InvalidArgument();
                    }
                    break;
                }
                case "get": {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    if (arguments.containsKey("key")) {
//                        reader.submit(() -> System.out.println(Arrays.toString(lsm.get(arguments.get("key").getBytes(StandardCharsets.UTF_8)))));
                        //lsm.get(arguments.get("key").getBytes(StandardCharsets.UTF_8));
                        try
                        {
                            System.out.println(Arrays.toString(lsm.get(arguments.get("key").getBytes(StandardCharsets.UTF_8))));
                        }
                        catch (RuntimeException e)
                        {
                            exception=e;
                        }
                    } else {
                        exception=new InvalidArgument();
                    }
                    break;
                }
                case "del": {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    if (arguments.containsKey("key")) {
                        byte[] key=arguments.get("key").getBytes(StandardCharsets.UTF_8);
                        write.submit(() ->
                        {
                            try {
                                lsm.delete(key);
                            }
                            catch (RuntimeException e)
                            {
                                exception=e;
                            }
                        });
                        //lsm.delete(arguments.get("key").getBytes(StandardCharsets.UTF_8));
                    } else {
                        exception=new InvalidArgument();
                    }
                    break;
                }
                //TODO uradi ovo
                case "stats": {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    System.out.println(lsm.stats());
                    break;
                }
                //todo proveri da li close radi lepo
                case "close": {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    lsm.close();
//                    ssTableWriter= Executors.newSingleThreadExecutor();
//                    compactionWorker=Executors.newSingleThreadScheduledExecutor();
//                    compactionLoop=Executors.newSingleThreadScheduledExecutor();
//                    write=Executors.newSingleThreadExecutor();
                    //todo videti da li ostati u beskonacnoj petlji
                    flag=false;
                    break;
                }
                case "flush-now":{
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    lsm.flushNow();
                    break;
                }
                case "list-sst":
                {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    lsm.listSst();
                    break;
                }
                case "sst-info":
                {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    String filename=arguments.get("filename");
                    if(filename==null)
                        exception= new InvalidArgument("Unesi filename");
                    lsm.sstInfo(arguments.get("filename"));
                    break;
                }
                case "manifest-info":
                {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    lsm.manifestInfo();
                    break;
                }
                case "version-info":
                {
                    if (lsm == null) {
                        exception = new RuntimeException("Moras da pozoves init");
                        continue;
                    }
                    lsm.versionInfo();
                    break;
                }
            }
            //arguments.clear();
        }
    }
}
