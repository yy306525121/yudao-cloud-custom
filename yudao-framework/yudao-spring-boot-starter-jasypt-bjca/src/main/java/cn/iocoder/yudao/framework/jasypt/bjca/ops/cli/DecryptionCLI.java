package cn.iocoder.yudao.framework.jasypt.bjca.ops.cli;
public final class DecryptionCLI {
    private DecryptionCLI() { }
    public static void main(String[] args) { System.exit(CliSupport.run("decrypt", args, System.out, System.err)); }
}
