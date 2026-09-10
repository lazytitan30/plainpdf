"""Generates the golden-file PDF fixtures used by the document-operation unit tests.

Run from the repo root:  python tools/make_fixtures.py

Every page of the generated documents carries two markers so tests can verify page order
without relying on text extraction: the visible text "PAGE n" and a MediaBox height of
700 + n points. Requires pypdf (and `cryptography` for the AES-256 fixture).
"""
from __future__ import annotations

import io
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "resources", "fixtures")


def build_pdf(page_count: int, rotate: dict[int, int] | None = None) -> bytes:
    """Hand-rolled minimal PDF: N pages of Helvetica text, distinct heights, optional /Rotate."""
    rotate = rotate or {}
    objects: list[bytes] = []

    def add(obj: bytes) -> int:
        objects.append(obj)
        return len(objects)

    font_id = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    pages_id = len(objects) + 1 + 2 * page_count  # allocated after the page objects
    page_ids = []
    for n in range(1, page_count + 1):
        text = f"PAGE {n}".encode()
        content = b"BT /F1 36 Tf 72 500 Td (" + text + b") Tj ET"
        stream_id = add(b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream")
        height = 700 + n
        rot = b" /Rotate %d" % rotate[n] if n in rotate else b""
        page = (
            b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 600 %d]%s "
            b"/Resources << /Font << /F1 %d 0 R >> >> /Contents %d 0 R >>"
            % (pages_id, height, rot, font_id, stream_id)
        )
        page_ids.append(add(page))
    kids = b" ".join(b"%d 0 R" % pid for pid in page_ids)
    real_pages_id = add(b"<< /Type /Pages /Kids [" + kids + b"] /Count %d >>" % page_count)
    assert real_pages_id == pages_id, (real_pages_id, pages_id)
    catalog_id = add(b"<< /Type /Catalog /Pages %d 0 R >>" % pages_id)

    out = io.BytesIO()
    out.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, obj in enumerate(objects, start=1):
        offsets.append(out.tell())
        out.write(b"%d 0 obj\n" % i + obj + b"\nendobj\n")
    xref = out.tell()
    out.write(b"xref\n0 %d\n" % (len(objects) + 1))
    out.write(b"0000000000 65535 f \n")
    for off in offsets:
        out.write(b"%010d 00000 n \n" % off)
    out.write(b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objects) + 1, catalog_id, xref))
    return out.getvalue()


def write(name: str, data: bytes) -> None:
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, "wb") as f:
        f.write(data)
    print(f"wrote {name} ({len(data)} bytes)")


def main() -> None:
    write("five_pages.pdf", build_pdf(5))
    write("three_pages.pdf", build_pdf(3))
    write("rotated.pdf", build_pdf(3, rotate={2: 90}))
    write("corrupt.pdf", b"%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\nthis is not a pdf body\n%%EOF")

    try:
        from pypdf import PdfReader, PdfWriter
    except ImportError:
        print("pypdf missing: skipping encrypted and outline fixtures", file=sys.stderr)
        return

    base = PdfReader(io.BytesIO(build_pdf(5)))

    writer = PdfWriter()
    for page in base.pages:
        writer.add_page(page)
    try:
        writer.encrypt(user_password="user1", owner_password="owner1", algorithm="AES-256")
    except Exception as e:  # cryptography not installed
        print(f"AES-256 unavailable ({e}); falling back to RC4", file=sys.stderr)
        writer.encrypt(user_password="user1", owner_password="owner1")
    buf = io.BytesIO()
    writer.write(buf)
    write("encrypted.pdf", buf.getvalue())

    writer = PdfWriter()
    for page in base.pages:
        writer.add_page(page)
    intro = writer.add_outline_item("Introduction", 0)
    writer.add_outline_item("Details", 1, parent=intro)
    writer.add_outline_item("Conclusion", 4)
    buf = io.BytesIO()
    writer.write(buf)
    write("outline.pdf", buf.getvalue())


if __name__ == "__main__":
    main()
