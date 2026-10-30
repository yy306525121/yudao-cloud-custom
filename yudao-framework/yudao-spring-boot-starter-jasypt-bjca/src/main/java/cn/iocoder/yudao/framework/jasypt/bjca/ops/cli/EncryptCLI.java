package cn.iocoder.yudao.framework.jasypt.bjca.ops.cli;
public final class EncryptCLI {
    private EncryptCLI() { }
    public static void main(String[] args) { System.exit(CliSupport.run("encrypt", args, System.out, System.err)); }
}
