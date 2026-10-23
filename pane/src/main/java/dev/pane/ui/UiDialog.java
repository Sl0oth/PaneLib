package dev.pane.ui;

/** Copy and actions for a native confirmation dialog. */
public record UiDialog(String title, String message, String confirmLabel, String confirmAction,
                       String cancelLabel, String cancelAction, UiDialogStyle style) {
    /** Requires a title, a message, and a label and action for both confirm and cancel. */
    public UiDialog {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Dialog title is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Dialog message is required");
        if (confirmLabel == null || confirmLabel.isBlank() || confirmAction == null || confirmAction.isBlank())
            throw new IllegalArgumentException("Dialog confirmation is required");
        if (cancelLabel == null || cancelLabel.isBlank() || cancelAction == null || cancelAction.isBlank())
            throw new IllegalArgumentException("Dialog cancellation is required");
    }

    /** Creates a dialog that inherits the document's default look. */
    public UiDialog(String title, String message, String confirmLabel, String confirmAction,
                    String cancelLabel, String cancelAction) {
        this(title, message, confirmLabel, confirmAction, cancelLabel, cancelAction, null);
    }
}
