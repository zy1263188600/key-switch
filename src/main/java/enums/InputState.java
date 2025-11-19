package enums;

public enum InputState {
    ENGLISH("英文输入法"),
    CHINESE("中文输入法");

    public final String name;

    InputState(String name) {
        this.name = name;
    }
}
