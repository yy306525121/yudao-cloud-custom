package cn.iocoder.yudao.framework.jasypt.bjca.ops.cli;
public final class GenerateMainKeyCLI {
    private GenerateMainKeyCLI() { }
    public static void main(String[] args) { System.exit(CliSupport.run("create", args, System.out, System.err)); }
}
