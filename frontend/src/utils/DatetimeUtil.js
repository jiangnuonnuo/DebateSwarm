export const formatDateTime = (val) => {
    if (!val) return '-';
    let date;
    if (val instanceof Date) {
        date = val;
    } else {
        const timestamp = Number(val);
        if (!isNaN(timestamp)) {
            date = new Date(timestamp);
        } else {
            const str = String(val);
            const main = str.replace('T', ' ').replace('Z', '').split('.')[0];
            return main || str;
        }
    }
    
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    const ss = String(date.getSeconds()).padStart(2, '0');
    return `${y}-${m}-${d} ${hh}:${mm}:${ss}`;
};

export const formatTime = (val) => {
    if (!val) return '';
    let date;
    const timestamp = Number(val);
    if (!isNaN(timestamp)) {
        date = new Date(timestamp);
    } else if (val instanceof Date) {
        date = val;
    } else {
        return String(val);
    }
    
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    
    if (isToday) {
        return `${hh}:${mm}`;
    } else {
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${m}-${d} ${hh}:${mm}`;
    }
};
