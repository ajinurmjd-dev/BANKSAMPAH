package id.banksampah.core;

public final class AdminSession {
    private static final AdminSession INSTANCE = new AdminSession();
    private BankService.Admin admin;

    private AdminSession() { }

    public static AdminSession getInstance() {
        return INSTANCE;
    }

    public synchronized BankService.Admin getAdmin() {
        return admin;
    }

    public synchronized void logout() {
        admin = null;
    }

    synchronized void login(BankService.Admin value) {
        admin = value;
    }
}
