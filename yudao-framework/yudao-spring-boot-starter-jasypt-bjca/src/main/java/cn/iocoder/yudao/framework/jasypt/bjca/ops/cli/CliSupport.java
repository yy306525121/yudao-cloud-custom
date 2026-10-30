package cn.iocoder.yudao.framework.jasypt.bjca.ops.cli;
import cn.iocoder.yudao.framework.jasypt.bjca.config.MscpProperties;
import cn.iocoder.yudao.framework.jasypt.bjca.core.BjcaClient;
import java.io.PrintStream;
import java.util.*;

final class CliSupport {
    private CliSupport() { }
    static int run(String operation, String[] args, PrintStream out, PrintStream err) {
        try {
            Map<String, String> values = new HashMap<>();
            Set<String> allowed = new HashSet<>(List.of("appId", "secret", "url", "deviceId", "verbose"));
            allowed.addAll(operation.equals("create") ? List.of("alias", "description") : List.of("input", "mainKeyId"));
            for (int i = 0; i < args.length; i++) {
                if (i == 0 && !args[i].contains("=")) continue;
                String[] pair = args[i].split("=", 2);
                if (pair.length != 2 || !allowed.contains(pair[0]) || pair[1].isBlank()) throw new IllegalArgumentException();
                values.put(pair[0], pair[1]);
            }
            for (String key : allowed) if (!key.equals("verbose") && !values.containsKey(key)) throw new IllegalArgumentException();
            MscpProperties p = new MscpProperties();
            p.setUrl(values.get("url")); p.setAppId(values.get("appId"));
            p.setDeviceId(values.get("deviceId")); p.setSecret(values.get("secret"));
            BjcaClient client = new BjcaClient(p);
            switch (operation) {
                case "create" -> out.println(client.createKey(values.get("alias"), values.get("description")));
                case "encrypt" -> {
                    for (String input : values.get("input").split(",", -1))
                        out.println("ENC(" + client.encrypt(input, values.get("mainKeyId")) + ")");
                }
                case "decrypt" -> out.println(client.decrypt(values.get("input"), values.get("mainKeyId")));
                default -> throw new IllegalArgumentException();
            }
            return 0;
        } catch (Exception e) {
            err.println("BJCA CLI failed. Required: appId, secret, url, deviceId and "
                    + (operation.equals("create") ? "alias, description" : "input, mainKeyId")
                    + "; use key=value arguments; verify parameters and BJCA service.");
            return 1;
        }
    }
}
