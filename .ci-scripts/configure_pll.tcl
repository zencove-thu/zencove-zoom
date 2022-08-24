# Set PLL frequency for cpu_clk
if {([info exists env(PLL_FREQ)])} {
    set pll_freq $env(PLL_FREQ)
} else {
    set pll_freq 50
}
puts "PLL frequency is $pll_freq"
set_property -dict [list CONFIG.CLKOUT1_REQUESTED_OUT_FREQ $pll_freq] [get_ips clk_pll]
