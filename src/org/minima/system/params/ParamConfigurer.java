package org.minima.system.params;

import static java.nio.file.Files.lines;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.minima.system.params.ParamConfigurer.ParamKeys.toParamKey;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.minima.system.network.p2p.P2PFunctions;
import org.minima.system.network.p2p.params.P2PParams;
import org.minima.utils.MinimaLogger;

public class ParamConfigurer {

    private final Map<ParamKeys, String> paramKeysToArg = new HashMap<>();
    private boolean daemon = false;
    private boolean rpcenable = false;
    private boolean mShutdownhook = true;
    
    public ParamConfigurer usingConfFile(String[] programArgs) {
        List<String> zArgsList = Arrays.asList(programArgs);
        int confArgKeyIndex = zArgsList.indexOf("-" + ParamKeys.conf);

        if (confArgKeyIndex > -1) {
            final String confFileArgValue = zArgsList.get(confArgKeyIndex + 1);
            File confFile = new File(confFileArgValue);

            try {
                paramKeysToArg.putAll(lines(confFile.toPath())
                        .map(line -> {
                            final String[] split = line.split("=");
                            return new Pair(split[0], split.length > 1 ? split[1] : "");
                        })
                        .map(pair -> toParamKey((String) pair.left)
                                .map(paramKey -> new Pair(paramKey, pair.right)))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(toMap(
                                pair -> (ParamKeys) pair.left,
                                pair -> (String) pair.right)));
            } catch (IOException exception) {
                System.out.println("Unable to read conf file.");
                System.exit(1);
            }
        }
        return this;
    }

    public ParamConfigurer usingEnvVariables(Map<String, String> envVariableMap) {
        paramKeysToArg.putAll(envVariableMap.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().startsWith("minima_"))
                .collect(toMap(
                        entry -> entry.getKey().toLowerCase().replaceFirst("minima_", ""),
                        entry -> ofNullable(entry.getValue())
                                .map(String::toLowerCase)
                                .orElse("")))
                .entrySet().stream()
                .filter(e -> toParamKey(e.getKey()).isPresent())
                .collect(toMap(entry -> toParamKey(entry.getKey()).get(), Map.Entry::getValue)));
        return this;
    }

    public ParamConfigurer usingProgramArgs(String[] programArgs) {

        int arglen = programArgs.length;
        int index = 0;
            while (index < arglen) {
                String arg = programArgs[index];
                final int imuCounter = index;
                progArgsToParamKey(arg)
                        .ifPresent(paramKey -> paramKeysToArg.put(paramKey,
                                lookAheadToNonParamKeyArg(programArgs, imuCounter).orElse("true")));
                index++;
            }
        MinimaLogger.log("Config Parameters");
        for (Map.Entry<ParamKeys, String> entry : paramKeysToArg.entrySet()) {
            MinimaLogger.log(entry.getKey() + ":" + entry.getValue());
        }
        return this;
    }

    public ParamConfigurer configure() {
        paramKeysToArg.forEach((key, value) -> key.consumer.accept(value, this));
        return this;
    }

    private static Optional<String> lookAheadToNonParamKeyArg(String[] programArgs, int currentIndex) {
        if (currentIndex + 1 <= programArgs.length - 1 ) {
            if (!progArgsToParamKey(programArgs[currentIndex + 1]).isPresent()) {
                return ofNullable(programArgs[currentIndex + 1]);
            }
        }
        return empty();
    }

    private static Optional<ParamKeys> progArgsToParamKey(String str) {
        if (str.startsWith("-")) {
            return of(ParamKeys.toParamKey(str.replaceFirst("-", ""))
                    .orElseThrow(() -> new UnknownArgumentException(str)));
        }
        return empty();
    }

    public boolean isDaemon() {
        return daemon;
    }

    public boolean isRpcenable() {
        return rpcenable;
    }
    
    public boolean isShutDownHook() {
        return mShutdownhook;
    }

    enum ParamKeys {
    	data("data", "Specify the data folder (defaults to .minima/ under user home", (args, configurer) -> {
    		//Get that folder
    		File dataFolder 	= new File(args);
    				
    		//Depends on the Base Minima Version
    		File minimafolder 	= new File(dataFolder,GlobalParams.MINIMA_BASE_VERSION);
    		minimafolder.mkdirs();
    		
    		//Set this globally
    		GeneralParams.DATA_FOLDER 	= minimafolder.getAbsolutePath();
        }),
    	basefolder("basefolder", "Specify a default file creation / backup / restore folder", (args, configurer) -> {
    		//Get that folder
    		File backupfolder 	= new File(args);
    		backupfolder.mkdirs();
    		
    		//Set this globally
    		GeneralParams.BASE_FILE_FOLDER = backupfolder.getAbsolutePath();
        }),
    	host("host", "Specify the host IP", (arg, configurer) -> {
            GeneralParams.MINIMA_HOST = arg;
            GeneralParams.IS_HOST_SET = true;
        }),
        port("port", "Specify the Minima port", (arg, configurer) -> {
            GeneralParams.MINIMA_PORT = Integer.parseInt(arg);
        }),
        rpc("rpc", "Specify the RPC port", (arg, configurer) -> {
//            GeneralParams.RPC_PORT = Integer.parseInt(arg);
            MinimaLogger.log("-rpc is no longer in use. Your RPC port is: " + GeneralParams.RPC_PORT);

        }),
        rpcenable("rpcenable", "Enable rpc", (args, configurer) -> {
            if ("true".equals(args)) {
                configurer.rpcenable = true;
            }
        }),
        allowallip("allowallip", "Allow all IP for Maxima", (args, configurer) -> {
            if ("true".equals(args)) {
            	GeneralParams.ALLOW_ALL_IP = true;
            }
        }),
        mdsenable("mdsenable", "Enable MDS", (args, configurer) -> {
            if ("true".equals(args)) {
            	GeneralParams.MDS_ENABLED = true;
            }
        }),
        mdspassword("mdspassword", "Specify the Minima MDS password", (arg, configurer) -> {
            GeneralParams.MDS_PASSWORD = arg.trim();
        }),
        mdsinit("mdsinit", "Specify a folder of MiniDAPPs", (arg, configurer) -> {
        	//Get that folder
    		File initFolder 	= new File(arg);
    		initFolder.mkdirs();
    		
        	GeneralParams.MDS_INITFOLDER= initFolder.getAbsolutePath();
        }),
        mdswrite("mdswrite", "Make an init MiniDAPP WRITE access", (arg, configurer) -> {
        	GeneralParams.MDS_WRITE= arg;
        }),
        conf("conf", "Specify a configuration file (absolute)", (args, configurer) -> {
            // do nothing
        }),
        daemon("daemon", "Run in daemon mode with no stdin input ( services )", (args, configurer) -> {
            if ("true".equals(args)) {
                configurer.daemon = true;
            }
        }),
        isclient("isclient", "Tells the P2P System that this node can't accept incoming connections", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.IS_ACCEPTING_IN_LINKS = false;
            }
        }),
        desktop("desktop", "Use Desktop settings - this node can't accept incoming connections", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.IS_ACCEPTING_IN_LINKS = false;
            }
        }),
        server("server", "Use Server settings - this node can accept incoming connections", (args, configurer) -> {
        	GeneralParams.IS_ACCEPTING_IN_LINKS = true;
//        	if ("true".equals(args)) {
//                GeneralParams.IS_ACCEPTING_IN_LINKS = false;
//            }
        }),
        mobile("mobile", "Sets this device to a mobile device - used for metrics only", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.IS_MOBILE = true;
            }
        }),
        nop2p("nop2p", "Disable the automatic P2P system", (args, configurer) -> {
            GeneralParams.P2P_ENABLED = false;
        }),
        noshutdownhook("noshutdownhook", "Do not use the shutdown hook (Android)", (args, configurer) -> {
        	configurer.mShutdownhook = false;
        }),
        noconnect("noconnect", "Stops the P2P system from connecting to other nodes until it's been connected too", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.NOCONNECT = true;
            }
        }),
        p2pnode("p2pnode", "Specify the initial P2P host:port list to connect to", (args, configurer) -> {
            GeneralParams.P2P_ROOTNODE = args;
        }),
        p2ploglevelinfo("p2p-log-level-info", "Set the P2P log level to info", (args, configurer) -> {
            P2PParams.LOG_LEVEL = P2PFunctions.Level.INFO;
        }),
        p2plogleveldebug("p2p-log-level-debug", "Set the P2P log level to info", (args, configurer) -> {
            P2PParams.LOG_LEVEL = P2PFunctions.Level.DEBUG;
        }),
//        automine("automine", "Simulate user traffic to construct the blockchain", (args, configurer) -> {
//            if ("true".equals(args)) {
//                GeneralParams.AUTOMINE = true;
//            }
//        }),
//        noautomine("noautomine", "Do not simulate user traffic to construct the blockchain", (args, configurer) -> {
//            GeneralParams.AUTOMINE = false;
//        }),
        connect("connect", "Disable the p2p and manually connect to this list of host:port", (args, configurer) -> {
            GeneralParams.P2P_ENABLED = false;
            GeneralParams.CONNECT_LIST = args;
        }),
        clean("clean", "Wipe data folder at startup", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.CLEAN = true;
            }
        }),
        mysqlhost("mysqlhost", "Store all archive data in a MySQL DB", (args, configurer) -> {
            GeneralParams.MYSQL_HOST = args;
        }),
        mysqldb("mysqldb", "The MySQL Database", (args, configurer) -> {
        	GeneralParams.MYSQL_DB = args;
        }),
        mysqluser("mysqluser", "The MySQL User", (args, configurer) -> {
        	GeneralParams.MYSQL_USER = args;
        }),
        mysqlpassword("mysqlpassword", "The MySQL Password", (args, configurer) -> {
        	GeneralParams.MYSQL_PASSWORD = args;
        }),
        genesis("genesis", "Create a genesis block, -clean and -automine", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.CLEAN = true;
//                GeneralParams.PRIVATE_NETWORK = true;
                GeneralParams.GENESIS = true;
//                GeneralParams.AUTOMINE = true;
            }
        }),
        test("test", "Use test params on a private network", (args, configurer) -> {
            if ("true".equals(args)) {
                GeneralParams.TEST_PARAMS 		= true;
//                GeneralParams.PRIVATE_NETWORK 	= true;
//                GeneralParams.P2P_ENABLED 		= false;
                TestParams.setTestParams();
            }
        }),
        help("help", "Print this help", (args, configurer) -> {
            System.out.println("Minima Help");
            stream(values())
                    .forEach(pk -> System.out.format("%-20s%-15s%n", new Object[] {"-" + pk.key,pk.helpMsg}));
            System.exit(1);
        });

        private final String key;
        private String helpMsg;
        private final BiConsumer<String, ParamConfigurer> consumer;

        ParamKeys(String key, String helpMsg, BiConsumer<String, ParamConfigurer> consumer) {
            this.key = key;
            this.helpMsg = helpMsg;
            this.consumer = consumer;
        }

        static Optional<ParamKeys> toParamKey(String str) {
           return stream(ParamKeys.values())
                   .filter(pk -> pk.key.equals(str))
                   .findFirst();
        }
    }

    public static class UnknownArgumentException extends RuntimeException {
        public UnknownArgumentException(String arg) {
            super("Unknown argument : " + arg);
        }
    }

    static class Pair<L, R> {
        L left;
        R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }
    }
}