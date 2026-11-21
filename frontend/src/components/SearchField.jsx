import { IconButton, InputAdornment, TextField } from "@mui/material";
import SearchOutlined from "@mui/icons-material/SearchOutlined";
import CloseRounded from "@mui/icons-material/CloseRounded";

/*
 * Search input with a leading icon and a clear button.
 */
export default function SearchField({
    value,
    onChange,
    placeholder = "Search…",
    label = "Search",
    sx
}) {
    return (
        <TextField
            size="small"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder={placeholder}
            sx={{ width: { xs: "100%", sm: 300 }, ...sx }}
            slotProps={{
                htmlInput: { "aria-label": label },
                input: {
                    startAdornment: (
                        <InputAdornment position="start">
                            <SearchOutlined fontSize="small" sx={{ color: "text.disabled" }} />
                        </InputAdornment>
                    ),
                    endAdornment: value ? (
                        <InputAdornment position="end">
                            <IconButton
                                size="small"
                                edge="end"
                                aria-label="Clear search"
                                onClick={() => onChange("")}
                            >
                                <CloseRounded fontSize="small" />
                            </IconButton>
                        </InputAdornment>
                    ) : null
                }
            }}
        />
    );
}
