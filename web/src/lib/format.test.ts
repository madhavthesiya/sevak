import {
  billNumber,
  formatClock,
  formatDateTime,
  formatMinutes,
  formatMoney,
  formatMonth,
  formatRupees,
  indiaDate,
  initials,
} from "./format";

// Non-breaking spaces can appear in currency output on some runtimes.
const plain = (s: string) => s.replace(/ /g, " ");

describe("money", () => {
  it("uses Indian digit grouping and two decimals", () => {
    expect(plain(formatMoney(1500))).toBe("₹1,500.00");
    expect(plain(formatMoney(150000))).toBe("₹1,50,000.00");
  });
  it("treats a missing amount as zero", () => {
    expect(plain(formatMoney(null))).toBe("₹0.00");
  });
  it("drops paise on rate cards", () => {
    expect(plain(formatRupees(2200))).toBe("₹2,200");
  });
});

describe("dates", () => {
  it("shows the roadmap format in India time", () => {
    expect(formatDateTime("2026-04-05T04:30:00Z")).toBe("Sun, 5 Apr · 10:00 AM");
  });
  it("handles noon and midnight on a 12-hour clock", () => {
    expect(formatDateTime("2026-04-05T06:30:00Z")).toBe("Sun, 5 Apr · 12:00 PM");
    expect(formatDateTime("2026-04-04T18:30:00Z")).toBe("Sun, 5 Apr · 12:00 AM");
  });
  it("uses the date in India, not the browser's zone", () => {
    expect(indiaDate("2026-04-04T19:00:00Z")).toBe("2026-04-05");
  });
  it("formats chart months and working hours", () => {
    expect(formatMonth("2026-04")).toBe("Apr 26");
    expect(formatClock("14:00:00")).toBe("2:00 PM");
    expect(formatClock("00:30")).toBe("12:30 AM");
  });
});

describe("small labels", () => {
  it("formats durations", () => {
    expect(formatMinutes(45)).toBe("45 min");
    expect(formatMinutes(60)).toBe("1 hr");
    expect(formatMinutes(90)).toBe("1 hr 30 min");
  });
  it("makes initials from at most two words", () => {
    expect(initials("Ravi Kumar")).toBe("RK");
    expect(initials("Karan  Singh Malhotra")).toBe("KS");
  });
  it("pads bill numbers", () => {
    expect(billNumber(42)).toBe("00042");
  });
});
