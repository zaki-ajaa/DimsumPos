"use strict";
/* app.js - tampilan UniCashier Dimsum Mentai.
   Semua data & logika (sorting, searching, checkout, file) diproses Java lewat /api/...
   File ini hanya menampilkan data dan mengirim input pengguna. */

const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const rupiah = (n) => "Rp " + Number(n).toLocaleString("id-ID");
/** Escape teks agar aman dimasukkan ke innerHTML (nama produk diketik pengguna). */
const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const debounce = (fn, ms = 250) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

const state = {
  meta: { kategori: [], metode: [], batas_menipis: 10 },
  katalog: [],
  cart: new Map(),                                 // sku -> {p, qty}
  pilih: new Map(),                                // sku -> jumlah yang dipilih di kartu produk
  katKasir: "", katProduk: "", metode: "Tunai",
  menipisKasir: false, menipisProduk: false,
  sortProduk: { kolom: null, desc: false },
  sortRiwayat: { kolom: "waktu", desc: true },
  produkDiubah: null,                              // sku yang sedang diedit (null = tambah)
};

/* ---------- ilustrasi produk (SVG, palet Madder/Orange/Sandy/Sunset/Olive) ---------- */
const ILUSTRASI = {
  Dimsum: `<svg viewBox="0 0 64 64" aria-hidden="true"><ellipse cx="32" cy="47" rx="23" ry="6" fill="#D9B8AB"/><path d="M11 44C11 28 21 17 32 17s21 11 21 27z" fill="#FBF3EE" stroke="#D9B8AB" stroke-width="2"/><path d="M18 31q4.5 6 9 0t9 0 9 0" fill="none" stroke="#AD535E" stroke-width="4.5" stroke-linecap="round"/><path d="M24 20l2 8M32 18v9M40 20l-2 8" stroke="#D9B8AB" stroke-width="1.6" stroke-linecap="round"/></svg>`,
  Topping: `<svg viewBox="0 0 64 64" aria-hidden="true"><rect x="25" y="9" width="14" height="9" rx="3" fill="#5E1522"/><path d="M22 20h20v28a6 6 0 0 1-6 6H28a6 6 0 0 1-6-6z" fill="#AD535E"/><rect x="26" y="30" width="12" height="12" rx="3" fill="#F0E3DB"/><path d="M32 14v-5" stroke="#5E1522" stroke-width="2"/></svg>`,
  Minuman: `<svg viewBox="0 0 64 64" aria-hidden="true"><path d="M33 4l6 12" stroke="#AD535E" stroke-width="3" stroke-linecap="round"/><path d="M19 20h26l-3.5 34a3 3 0 0 1-3 2.5H25.5a3 3 0 0 1-3-2.5z" fill="#D9B8AB"/><rect x="16" y="15" width="32" height="7" rx="3.5" fill="#A02B3D"/><rect x="25" y="30" width="8" height="8" rx="2" fill="#fff" opacity=".75"/><rect x="34" y="37" width="7" height="7" rx="2" fill="#fff" opacity=".75"/></svg>`,
  Paket: `<svg viewBox="0 0 64 64" aria-hidden="true"><rect x="12" y="28" width="40" height="26" rx="5" fill="#D9B8AB"/><rect x="9" y="19" width="46" height="11" rx="4" fill="#AD535E"/><rect x="29.5" y="19" width="5" height="35" fill="#A02B3D"/><path d="M32 19c-4-9-12-8-11-3s8 3 11 3zM32 19c4-9 12-8 11-3s-8 3-11 3z" fill="none" stroke="#A02B3D" stroke-width="2.5"/></svg>`,
};
const ilustrasi = (kategori) => ILUSTRASI[kategori] ||
  `<svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="32" r="18" fill="#D9B8AB"/><circle cx="32" cy="32" r="8" fill="#AD535E"/></svg>`;
const tile = (kategori, ukuran = "") => `<div class="tile ${ukuran === true ? "kecil" : ukuran}">${ilustrasi(kategori)}</div>`;
const ICON_HAPUS = `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12"/></svg>`;

/** Panggil API Java; lempar Error dengan pesan dari server bila gagal. */
async function api(jalur, { metode = "GET", body } = {}) {
  const res = await fetch(jalur, {
    method: metode,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let data = {};
  try { data = await res.json(); } catch { /* respons bukan JSON */ }
  if (!res.ok) throw new Error(data.error || "Terjadi kesalahan");
  return data;
}

let timerToast;
function toast(pesan, galat = false) {
  const el = $("#toast");
  el.textContent = pesan;
  el.className = "tampil" + (galat ? " galat-toast" : "");
  clearTimeout(timerToast);
  timerToast = setTimeout(() => (el.className = ""), 3400);
}
/** Bungkus aksi async agar error tampil sebagai toast, bukan crash. */
const aman = (fn) => async (...a) => { try { await fn(...a); } catch (e) { toast(e.message, true); } };
const qs = (obj) => new URLSearchParams(Object.entries(obj).filter(([, v]) => v !== "" && v != null)).toString();

function setBadgeMenipis(n) {
  const b = $("#badge-menipis");
  b.textContent = n;
  b.hidden = !n;
}

/** Gambar deretan chip (tombol pilihan). items: [{v, t}]; aktif: nilai terpilih. */
function gambarChips(el, items, aktif) {
  el.innerHTML = items.map(({ v, t }) =>
    `<button class="chip" data-v="${esc(v)}" aria-pressed="${v === aktif}">${esc(t)}</button>`).join("");
}

/* ---------- tabel yang bisa diurutkan ---------- */
/** kolom: [{k, t, fmt?, angka?}]; sort: {kolom, desc}; aksi: fungsi baris -> html tombol aksi */
function gambarTabel(el, kolom, baris, sort, { aksi, kelasBaris, kosong = "Tidak ada data" } = {}) {
  const th = kolom.map((c) => {
    const panah = sort && sort.kolom === c.k ? (sort.desc ? " ▼" : " ▲") : "";
    const aria = sort && sort.kolom === c.k ? `aria-sort="${sort.desc ? "descending" : "ascending"}"` : "";
    return `<th ${sort ? `data-k="${c.k}" tabindex="0"` : ""} ${aria} class="${c.angka ? "angka" : ""}">${esc(c.t)}${panah}</th>`;
  }).join("") + (aksi ? "<th></th>" : "");
  const tr = baris.map((r, i) => {
    const td = kolom.map((c) => `<td class="${c.angka ? "angka" : ""}">${c.fmt ? c.fmt(r[c.k], r) : esc(r[c.k])}</td>`).join("");
    return `<tr data-i="${i}" class="${kelasBaris ? kelasBaris(r) : ""}">${td}${aksi ? `<td class="angka">${aksi(r)}</td>` : ""}</tr>`;
  }).join("");
  el.innerHTML = `<thead><tr>${th}</tr></thead><tbody>${tr || `<tr><td class="kosong-tabel" colspan="${kolom.length + 1}">${kosong}</td></tr>`}</tbody>`;
}
function ubahUrut(sort, kolom) {
  sort.desc = sort.kolom === kolom ? !sort.desc : false;
  sort.kolom = kolom;
}

/* ---------- sidebar: mode lebar / ikon saja ---------- */
function aturSidebar(mini, simpan = true) {
  $("#shell").dataset.mini = mini ? "1" : "0";
  const b = $("#btn-sidebar");
  b.setAttribute("aria-expanded", String(!mini));
  b.setAttribute("aria-label", mini ? "Lebarkan menu" : "Ciutkan menu");
  if (simpan) { try { localStorage.setItem("sidebarMini", mini ? "1" : "0"); } catch { /* storage dimatikan */ } }
}

/* ---------- navigasi ---------- */
const muat = { kasir: aman(muatKatalog), produk: aman(muatProduk), riwayat: aman(muatRiwayat), laporan: aman(muatLaporan) };
function tampil(nama) {
  $$("#menu button").forEach((b) => {
    const aktif = b.dataset.view === nama;
    b.classList.toggle("aktif", aktif);
    aktif ? b.setAttribute("aria-current", "page") : b.removeAttribute("aria-current");
  });
  $$(".view").forEach((v) => v.classList.toggle("aktif", v.id === "view-" + nama));
  $("#shell").dataset.view = nama;
  muat[nama]();
}

/* ---------- KASIR ---------- */
async function muatKatalog() {
  const d = await api("/api/produk?" + qs({
    kata: $("#k-cari").value, kategori: state.katKasir, menipis: state.menipisKasir ? "1" : "" }));
  state.katalog = d.produk;
  setBadgeMenipis(d.menipis);
  $("#k-jumlah").textContent = `${d.produk.length} produk`;
  $("#k-katalog").innerHTML = d.produk.map((p) => {
    const menipis = p.stok <= state.meta.batas_menipis;
    const n = state.pilih.get(p.sku) || 1;
    const habis = p.stok <= 0;
    return `<article class="produk-kartu ${habis ? "habis" : ""}" data-sku="${esc(p.sku)}">
      <div class="tile besar">${ilustrasi(p.kategori)}<span class="kat-tag">${esc(p.kategori)}</span>${habis ? `<span class="tag pojok">Habis</span>` : menipis ? `<span class="tag pojok">Stok menipis</span>` : ""}</div>
      <h3>${esc(p.nama)}</h3>
      <div class="info"><span class="harga">${rupiah(p.harga)}</span><span class="stok-teks">${habis ? "Stok 0" : "Stok " + p.stok}</span></div>
      <div class="aksi-kartu">
        <div class="stepper" role="group" aria-label="Jumlah ${esc(p.nama)}">
          <button data-aksi="kurang" aria-label="Kurangi jumlah" ${habis ? "disabled" : ""}>−</button>
          <output>${n}</output>
          <button data-aksi="tambah" aria-label="Tambah jumlah" ${habis ? "disabled" : ""}>+</button>
        </div>
        <button class="btn tambah" data-aksi="add" ${habis ? "disabled" : ""}>Tambah</button>
      </div></article>`;
  }).join("") || `<p class="kosong">Produk tidak ditemukan</p>`;
}
function tambahKeCart(sku, jumlah = 1) {
  const p = state.katalog.find((x) => x.sku === sku) || state.cart.get(sku)?.p;
  if (!p) return;
  const qty = (state.cart.get(sku)?.qty || 0) + jumlah;
  if (qty > p.stok) return toast(`Stok ${p.nama} hanya ${p.stok}`, true);
  state.cart.set(sku, { p, qty });
  gambarCart();
}
function ubahQty(sku, delta) {
  const item = state.cart.get(sku);
  if (!item) return;
  if (delta > 0) return tambahKeCart(sku);
  item.qty -= 1;
  if (item.qty <= 0) state.cart.delete(sku);
  gambarCart();
}
function gambarCart() {
  const items = [...state.cart.values()];
  $("#c-items").innerHTML = items.map(({ p, qty }) => `
    <div class="cart-row">
      ${tile(p.kategori, true)}
      <div>
        <div class="nama">${esc(p.nama)}</div>
        <div class="sub">${rupiah(p.harga * qty)}</div>
        <div class="cart-ctl">
          <div class="stepper" role="group" aria-label="Jumlah ${esc(p.nama)}">
            <button data-aksi="kurang" data-sku="${esc(p.sku)}" aria-label="Kurangi">−</button>
            <output>${qty}</output>
            <button data-aksi="tambah" data-sku="${esc(p.sku)}" aria-label="Tambah">+</button>
          </div>
          <button class="ikon-btn" data-aksi="hapus" data-sku="${esc(p.sku)}" aria-label="Hapus ${esc(p.nama)}">${ICON_HAPUS}</button>
        </div>
      </div>
    </div>`).join("") || `<div class="cart-kosong"><svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8h12l-1 12H7L6 8z"/><path d="M9 8a3 3 0 0 1 6 0"/></svg>Belum ada pesanan.<br>Pilih menu di sebelah kiri.</div>`;
  const jumlah = items.reduce((a, { qty }) => a + qty, 0);
  $("#c-nomor").textContent = jumlah ? `${jumlah} item` : "Pesanan baru";
  hitungTotal();
}
function hitungTotal() {
  const sub = [...state.cart.values()].reduce((a, { p, qty }) => a + p.harga * qty, 0);
  const persen = Math.min(100, Math.max(0, parseInt($("#c-diskon").value, 10) || 0));
  const diskon = Math.floor(sub * persen / 100);   // sama dengan perhitungan di Java (sub * persen / 100, pembagian bilangan bulat)
  const total = sub - diskon;
  const tunai = state.metode === "Tunai";
  $("#c-sub").textContent = rupiah(sub);
  $("#c-dis").textContent = "-" + rupiah(diskon);
  $("#c-tot").textContent = rupiah(total);
  $("#c-uang-wrap").style.display = $("#c-kem-wrap").style.display = tunai ? "" : "none";
  $("#c-kem").textContent = rupiah(Math.max(0, (parseInt($("#c-uang").value, 10) || 0) - total));
}
async function bayar() {
  if (!state.cart.size) return toast("Keranjang masih kosong", true);
  const d = await api("/api/checkout", { metode: "POST", body: {
    items: [...state.cart.values()].map(({ p, qty }) => ({ sku: p.sku, qty })),
    metode: state.metode,
    diskon: parseInt($("#c-diskon").value, 10) || 0,
    uang: parseInt($("#c-uang").value, 10) || 0,
  } });
  state.cart.clear();
  $("#c-uang").value = ""; $("#c-diskon").value = 0;
  gambarCart();
  await muatKatalog();
  tampilkanStruk(d.struk);
}
function tampilkanStruk(teks) {
  $("#struk-isi").textContent = teks;
  $("#dlg-struk").showModal();
}

/* ---------- PRODUK ---------- */
const KOLOM_PRODUK = [
  { k: "sku", t: "SKU" },
  { k: "nama", t: "Nama", fmt: (v, r) => `<span style="display:flex;align-items:center;gap:10px">${tile(r.kategori, true)}${esc(v)}</span>` },
  { k: "kategori", t: "Kategori" },
  { k: "harga", t: "Harga", fmt: rupiah, angka: true }, { k: "hpp", t: "HPP", fmt: rupiah, angka: true },
  { k: "stok", t: "Stok", angka: true, fmt: (v) => (v <= state.meta.batas_menipis ? `<span class="tag">menipis</span> ` : "") + esc(v) },
];
let daftarProduk = [];
async function muatProduk() {
  const s = state.sortProduk;
  const d = await api("/api/produk?" + qs({
    kata: $("#p-cari").value, kategori: state.katProduk, menipis: state.menipisProduk ? "1" : "",
    urut: s.kolom, arah: s.desc ? "desc" : "asc" }));
  daftarProduk = d.produk;
  setBadgeMenipis(d.menipis);
  gambarTabel($("#t-produk"), KOLOM_PRODUK, d.produk, s, {
    kelasBaris: (r) => (r.stok <= state.meta.batas_menipis ? "menipis" : ""),
    aksi: (r) => `<button class="btn kecil-btn" data-aksi="ubah" data-sku="${esc(r.sku)}">Ubah</button>
                  <button class="btn kecil-btn bahaya" data-aksi="hapus" data-sku="${esc(r.sku)}">Hapus</button>`,
  });
  $("#p-info").textContent = `${d.produk.length} dari ${d.total} produk ditampilkan · ${d.menipis} stok menipis (baris krem)`;
}
function bukaFormProduk(produk) {
  const f = $("#f-produk");
  state.produkDiubah = produk ? produk.sku : null;
  $("#f-judul").textContent = produk ? "Ubah produk" : "Tambah produk";
  f.reset();
  f.kategori.value = produk ? produk.kategori : state.meta.kategori[0];
  if (produk) for (const k of ["sku", "nama", "harga", "hpp", "stok"]) f[k].value = produk[k];
  $("#f-galat").textContent = "";
  $("#dlg-produk").showModal();
}
async function simpanProduk(e) {
  e.preventDefault();
  const body = Object.fromEntries(new FormData(e.target).entries());
  try {
    await api(state.produkDiubah ? "/api/produk/" + encodeURIComponent(state.produkDiubah) : "/api/produk",
      { metode: state.produkDiubah ? "PUT" : "POST", body });
  } catch (err) { $("#f-galat").textContent = err.message; return; }
  $("#dlg-produk").close();
  toast("Produk tersimpan");
  muatProduk();
}
async function hapusProduk(sku) {
  const p = daftarProduk.find((x) => x.sku === sku);
  if (!confirm(`Hapus ${p ? p.nama : sku}?`)) return;
  await api("/api/produk/" + encodeURIComponent(sku), { metode: "DELETE" });
  toast("Produk dihapus");
  muatProduk();
}

/* ---------- RIWAYAT ---------- */
const KOLOM_RIWAYAT = [
  { k: "id", t: "ID" }, { k: "waktu", t: "Waktu" }, { k: "items", t: "Item" },
  { k: "total", t: "Total", fmt: rupiah, angka: true }, { k: "metode", t: "Metode" },
];
let daftarRiwayat = [];
async function muatRiwayat() {
  const s = state.sortRiwayat;
  const d = await api("/api/transaksi?" + qs({ kata: $("#r-cari").value, urut: s.kolom, arah: s.desc ? "desc" : "asc" }));
  daftarRiwayat = d.transaksi;
  gambarTabel($("#t-riwayat"), KOLOM_RIWAYAT, d.transaksi, s, { kosong: "Belum ada transaksi" });
}

/* ---------- LAPORAN ---------- */
/** Grafik batang pendapatan (SVG) dari rekap harian; maksimal 7 hari terakhir. */
function gambarGrafik(harian) {
  const data = harian.slice(0, 7).reverse();
  if (!data.length) return `<div class="kosong-tabel">Belum ada penjualan</div>`;
  const maks = Math.max(...data.map((d) => d.pendapatan), 1);
  const lebar = 700, tinggi = 200, dasar = 170, gap = lebar / data.length, bar = Math.min(56, gap * 0.55);
  const batang = data.map((d, i) => {
    const h = Math.max(4, (d.pendapatan / maks) * 140), x = i * gap + (gap - bar) / 2;
    return `<g><title>${esc(d.tanggal)}: ${rupiah(d.pendapatan)}</title>
      <rect x="${x}" y="${dasar - h}" width="${bar}" height="${h}" rx="9" fill="${i === data.length - 1 ? "#A02B3D" : "#CF949B"}"/>
      <text x="${x + bar / 2}" y="${dasar - h - 8}" text-anchor="middle" font-size="12" font-weight="700" fill="#2A0E10">${rupiah(d.pendapatan).replace("Rp ", "")}</text>
      <text x="${x + bar / 2}" y="${dasar + 20}" text-anchor="middle" font-size="12" fill="#6E5350">${esc(d.tanggal.slice(5))}</text></g>`;
  }).join("");
  return `<svg viewBox="0 0 ${lebar} ${tinggi}" role="img" aria-label="Grafik pendapatan harian"><line x1="0" y1="${dasar}" x2="${lebar}" y2="${dasar}" stroke="#E6D3C8"/>${batang}</svg>`;
}
async function muatLaporan() {
  const d = await api("/api/laporan");
  const t = d.total;
  setBadgeMenipis(d.menipis);
  const kpi = [["k1", "Transaksi", t.jumlah], ["k2", "Pendapatan", rupiah(t.pendapatan)], ["k2", "Modal (HPP)", rupiah(t.hpp)],
               ["k3", "Laba kotor", rupiah(t.laba)], ["k4", "Stok menipis", d.menipis]];
  $("#l-kartu").innerHTML = kpi.map(([c, j, v]) => `<div class="kartu ${c}"><small>${j}</small><strong>${v}</strong></div>`).join("");
  $("#l-grafik").innerHTML = gambarGrafik(d.harian);
  gambarTabel($("#t-laporan"), [
    { k: "tanggal", t: "Tanggal" }, { k: "jumlah", t: "Transaksi", angka: true },
    { k: "pendapatan", t: "Pendapatan", fmt: rupiah, angka: true }, { k: "hpp", t: "HPP", fmt: rupiah, angka: true },
    { k: "laba", t: "Laba", fmt: rupiah, angka: true }], d.harian, null, { kosong: "Belum ada penjualan" });
}

/* ---------- inisialisasi & event ---------- */
function pasangChips(elId, ambil, simpan, muatFn) {
  $(elId).addEventListener("click", (e) => {
    const b = e.target.closest("button[data-v]"); if (!b) return;
    simpan(b.dataset.v);
    gambarChips($(elId), ambil.items(), b.dataset.v);
    muatFn();
  });
}
async function init() {
  state.meta = await api("/api/meta");
  const itemKat = [{ v: "", t: "Semua" }, ...state.meta.kategori.map((k) => ({ v: k, t: k }))];
  gambarChips($("#k-chips"), itemKat, state.katKasir);
  gambarChips($("#p-chips"), itemKat, state.katProduk);
  gambarChips($("#c-metode"), state.meta.metode.map((m) => ({ v: m, t: m })), state.metode);
  $("#f-produk").kategori.innerHTML = state.meta.kategori.map((x) => `<option>${esc(x)}</option>`).join("");
  const tgl = new Date().toLocaleDateString("id-ID", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  $("#s-tanggal").textContent = tgl;

  let tersimpan = null;
  try { tersimpan = localStorage.getItem("sidebarMini"); } catch { /* abaikan */ }
  aturSidebar(tersimpan === null ? window.innerWidth < 1280 : tersimpan === "1", false);
  $("#btn-sidebar").addEventListener("click", () => aturSidebar($("#shell").dataset.mini !== "1"));
  $("#struk-cetak").addEventListener("click", () => window.print());
  $("#menu").addEventListener("click", (e) => { const b = e.target.closest("button[data-view]"); if (b) tampil(b.dataset.view); });

  // kasir
  $("#k-cari").addEventListener("input", debounce(muat.kasir));
  pasangChips("#k-chips", { items: () => itemKat }, (v) => (state.katKasir = v), muat.kasir);
  $("#k-menipis").addEventListener("click", (e) => {
    state.menipisKasir = !state.menipisKasir;
    e.currentTarget.setAttribute("aria-pressed", state.menipisKasir);
    muat.kasir();
  });
  $("#k-katalog").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-aksi]"), kartu = e.target.closest("[data-sku]");
    if (!b || !kartu) return;
    const sku = kartu.dataset.sku, p = state.katalog.find((x) => x.sku === sku);
    let n = state.pilih.get(sku) || 1;
    if (b.dataset.aksi === "add") {
      const sisa = p.stok - (state.cart.get(sku)?.qty || 0);
      if (n > sisa) return toast(`Stok ${p.nama} hanya ${p.stok}`, true);
      tambahKeCart(sku, n);
      state.pilih.set(sku, 1);
      $("output", kartu).textContent = 1;
      b.textContent = "Ditambah ✓";
      setTimeout(() => { b.textContent = "Tambah"; }, 900);
      return;
    }
    n = b.dataset.aksi === "tambah" ? Math.min(p.stok, n + 1) : Math.max(1, n - 1);
    state.pilih.set(sku, n);
    $("output", kartu).textContent = n;
  });
  $("#c-items").addEventListener("click", (e) => {
    const b = e.target.closest("[data-aksi]"); if (!b) return;
    const { aksi, sku } = b.dataset;
    if (aksi === "hapus") { state.cart.delete(sku); gambarCart(); } else ubahQty(sku, aksi === "tambah" ? 1 : -1);
  });
  ["#c-diskon", "#c-uang"].forEach((s) => $(s).addEventListener("input", hitungTotal));
  $("#c-metode").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-v]"); if (!b) return;
    state.metode = b.dataset.v;
    gambarChips($("#c-metode"), state.meta.metode.map((m) => ({ v: m, t: m })), state.metode);
    hitungTotal();
  });
  $("#c-bayar").addEventListener("click", aman(bayar));
  $("#c-kosong").addEventListener("click", () => { state.cart.clear(); gambarCart(); });
  document.addEventListener("keydown", (e) => {
    if (e.key === "F9" && $("#view-kasir").classList.contains("aktif")) { e.preventDefault(); aman(bayar)(); }
  });

  // produk
  $("#p-cari").addEventListener("input", debounce(muat.produk));
  pasangChips("#p-chips", { items: () => itemKat }, (v) => (state.katProduk = v), muat.produk);
  $("#p-tambah").addEventListener("click", () => bukaFormProduk(null));
  const klikTabelProduk = aman(async (e) => {
    const th = e.target.closest("th[data-k]");
    if (th) { ubahUrut(state.sortProduk, th.dataset.k); return muatProduk(); }
    const b = e.target.closest("button[data-aksi]"); if (!b) return;
    if (b.dataset.aksi === "ubah") bukaFormProduk(daftarProduk.find((p) => p.sku === b.dataset.sku));
    else await hapusProduk(b.dataset.sku);
  });
  $("#t-produk").addEventListener("click", klikTabelProduk);
  $("#t-produk").addEventListener("keydown", (e) => { if (e.key === "Enter" && e.target.matches("th[data-k]")) klikTabelProduk(e); });
  $("#f-produk").addEventListener("submit", simpanProduk);
  $("#f-batal").addEventListener("click", () => $("#dlg-produk").close());

  // riwayat
  $("#r-cari").addEventListener("input", debounce(muat.riwayat));
  const klikTabelRiwayat = aman(async (e) => {
    const th = e.target.closest("th[data-k]");
    if (th) { ubahUrut(state.sortRiwayat, th.dataset.k); return muatRiwayat(); }
    const tr = e.target.closest("tbody tr[data-i]"); if (!tr) return;
    const d = await api("/api/struk/" + encodeURIComponent(daftarRiwayat[tr.dataset.i].id));
    tampilkanStruk(d.struk);
  });
  $("#t-riwayat").addEventListener("click", klikTabelRiwayat);
  $("#t-riwayat").addEventListener("keydown", (e) => { if (e.key === "Enter" && e.target.matches("th[data-k]")) klikTabelRiwayat(e); });
  $("#struk-tutup").addEventListener("click", () => $("#dlg-struk").close());

  if (state.meta.peringatan.length) toast("Peringatan data: " + state.meta.peringatan.join("; "), true);
  gambarCart();
  tampil("kasir");
}
init().catch((e) => toast("Gagal memuat aplikasi: " + e.message, true));
