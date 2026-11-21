import { IconButton, InputAdornment, TextField } from "@mui/material";
import LockOutlined from "@mui/icons-material/LockOutlined";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";

/*
 * Password input with a show / hide toggle
 * (used by the reset-password and account-activation pages).
 */
export default function PasswordField({ label, value, onChange, visible, onToggle, error, helperText, disabled, autoFocus }) {
    return (
        <TextField
            fullWidth
            label={label}
            type={visible ? "text" : "password"}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            autoComplete="new-password"
            autoFocus={autoFocus}
            required
            disabled={disabled}
            error={error}
            helperText={helperText}
            slotProps={{
                input: {
                    startAdornment: (
                        <InputAdornment position="start">
                            <LockOutlined fontSize="small" sx={{ color: "text.disabled" }} />
                        </InputAdornment>
                    ),
                    endAdornment: (
                        <InputAdornment position="end">
                            <IconButton
                                className="icon-swap"
                                edge="end"
                                size="small"
                                onClick={onToggle}
                                aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
                            >
                                {visible ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                            </IconButton>
                        </InputAdornment>
                    )
                }
            }}
        />
    );
}
