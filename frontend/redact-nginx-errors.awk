# Quoted request/upstream/header/file values are untrusted; preserve severity, errno and error description.
function redact(line,    out,i,ch,quote,escaped) {
    out=""; quote=""; escaped=0
    for (i=1; i<=length(line); i++) {
        ch=substr(line,i,1)
        if (quote!="") {
            if (escaped) escaped=0
            else if (ch=="\\") escaped=1
            else if (ch==quote) quote=""
        } else if (ch=="\"" || ch=="'") {
            quote=ch; out=out "[redacted]"
        } else out=out ch
    }
    gsub(/client: [^,]*/, "client: [redacted]",out)
    gsub(/host: [^,]*/, "host: [redacted]",out)
    gsub(/\?[^ ,\t]*/, "?[redacted]",out)
    return out
}
{ print redact($0); fflush() }
