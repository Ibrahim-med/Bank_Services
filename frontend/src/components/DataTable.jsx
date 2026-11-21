import { useState } from "react";
import {
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TablePagination,
    TableRow,
    useMediaQuery
} from "@mui/material";
import { useTheme } from "@mui/material/styles";

import LoadingState from "./LoadingState";

/*
 * Responsive, paginated data table.
 *
 * columns: [{
 *   key, header, render(row) -> node,
 *   align?: "left" | "right" | "center",
 *   nowrap?: boolean,
 *   width?: number | string,
 *   mobile?: "primary" | "aside" | "hidden" | "actions"
 * }]
 *
 * On small screens rows become cards: the "primary" column is
 * the card title, "aside" sits on its right, "actions" goes to
 * the bottom and every other column becomes a label/value pair.
 *
 * Change `resetKey` (e.g. the search term) to jump back to page 1;
 * the visible rows then fade in again.
 */
export default function DataTable({
    columns,
    rows,
    getRowId,
    loading = false,
    loadingLabel,
    empty,
    minWidth = 720,
    mobileBreakpoint = "md",
    rowsPerPageOptions = [10, 25, 50],
    defaultRowsPerPage = 10,
    paginate = true,
    resetKey,
    ariaLabel
}) {
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down(mobileBreakpoint));

    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(defaultRowsPerPage);
    const [lastResetKey, setLastResetKey] = useState(resetKey);

    // Return to the first page whenever the caller's filters change.
    if (resetKey !== lastResetKey) {
        setLastResetKey(resetKey);
        setPage(0);
    }

    // Loader only on the first load; on refresh the current rows stay
    // visible (no flicker) while the page shows its own activity state.
    if (loading && !rows.length) {
        return <LoadingState label={loadingLabel} />;
    }

    if (!rows.length) {
        return empty ?? null;
    }

    const lastPage = Math.max(0, Math.ceil(rows.length / rowsPerPage) - 1);
    const currentPage = Math.min(page, lastPage);

    const visibleRows = paginate
        ? rows.slice(currentPage * rowsPerPage, currentPage * rowsPerPage + rowsPerPage)
        : rows;

    const showPagination = paginate && rows.length > rowsPerPageOptions[0];

    /*
     * Changing this key remounts the rows, which replays their
     * staggered fade-in (motion.css). It changes with filters and
     * pagination, not when the same data is reloaded.
     */
    const resultsKey = `${String(resetKey ?? "")}|${currentPage}|${rowsPerPage}`;

    const pagination = showPagination && (
        <TablePagination
            component="div"
            count={rows.length}
            page={currentPage}
            onPageChange={(_event, nextPage) => setPage(nextPage)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(event) => {
                setRowsPerPage(Number(event.target.value));
                setPage(0);
            }}
            rowsPerPageOptions={rowsPerPageOptions}
            labelRowsPerPage={isMobile ? "Rows" : "Rows per page"}
        />
    );

    if (isMobile) {
        const primary = columns.find((column) => column.mobile === "primary") ?? columns[0];
        const aside = columns.find((column) => column.mobile === "aside");
        const actions = columns.find((column) => column.mobile === "actions");
        const details = columns.filter(
            (column) =>
                column !== primary &&
                column !== aside &&
                column !== actions &&
                column.mobile !== "hidden"
        );

        return (
            <>
                <ul key={resultsKey} className="data-cards" aria-label={ariaLabel}>
                    {visibleRows.map((row, index) => (
                        <li key={getRowId(row) ?? index} className="data-card">
                            <div className="data-card__head">
                                <div className="data-card__primary">{primary.render(row)}</div>
                                {aside && <div className="data-card__aside">{aside.render(row)}</div>}
                            </div>

                            {details.length > 0 && (
                                <dl className="data-card__details">
                                    {details.map((column) => (
                                        <div key={column.key} className="data-card__item">
                                            <dt>{column.header}</dt>
                                            <dd>{column.render(row)}</dd>
                                        </div>
                                    ))}
                                </dl>
                            )}

                            {actions && <div className="data-card__actions">{actions.render(row)}</div>}
                        </li>
                    ))}
                </ul>
                {pagination}
            </>
        );
    }

    return (
        <>
            <TableContainer className="data-table">
                <Table sx={{ minWidth }} aria-label={ariaLabel}>
                    <TableHead>
                        <TableRow>
                            {columns.map((column) => (
                                <TableCell
                                    key={column.key}
                                    align={column.align}
                                    sx={{ width: column.width }}
                                >
                                    {column.header}
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>

                    <TableBody key={resultsKey} className="data-rows">
                        {visibleRows.map((row, index) => (
                            <TableRow key={getRowId(row) ?? index} hover>
                                {columns.map((column) => (
                                    <TableCell
                                        key={column.key}
                                        align={column.align}
                                        sx={column.nowrap ? { whiteSpace: "nowrap" } : undefined}
                                    >
                                        {column.render(row)}
                                    </TableCell>
                                ))}
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
            {pagination}
        </>
    );
}
