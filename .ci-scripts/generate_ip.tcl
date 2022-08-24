# Based on LLCL-MIPS project(https://github.com/huang-jl/LLCL-MIPS)

update_compile_order -fileset sources_1

# Check jobs number
if {[info exists env(JOBS_NUMBER)]} {
    set jobs_number $env(JOBS_NUMBER)
} else {
    set jobs_number 2
}
puts "JOBS NUMBER is $jobs_number"

# Add our rtl
if { [file exists "./generated_verilog/mycpu_top.v"] == 1} {
    add_files -norecurse ./generated_verilog/mycpu_top.v
}
if { [file exists "./generated_verilog/mergeRTL.v"] == 1} {
    add_files -norecurse ./generated_verilog/mergeRTL.v
}

# Add our ips
set ip_paths [glob ./rtl/*/*.xci]
foreach ip $ip_paths {
    add_files -norecurse $ip
}

# change coe files
if {[info exists env(AXI_RAM_COE_FILE)]} {
    puts "COE is $env(AXI_RAM_COE_FILE)"
    # remove other coe_files
    remove_files inst_ram.coe
    remove_files axi_ram.coe
    # add new coe_files
    add_files -norecurse "[pwd]/$env(AXI_RAM_COE_FILE)"

    set_property -dict [list CONFIG.Coe_File "[pwd]/$env(AXI_RAM_COE_FILE)"] [get_ips axi_ram]
    set axi_ram_ip_dir [get_property IP_DIR [get_ips axi_ram]]
    generate_target all [get_files $axi_ram_ip_dir/axi_ram.xci]
    export_ip_user_files -of_objects [get_files $axi_ram_ip_dir/axi_ram.xci] -no_script -sync -force -quiet
}

# Set PLL frequency for cpu_clk
if {([info exists env(PLL_FREQ)])} {
    set pll_freq $env(PLL_FREQ)
} else {
    set pll_freq 50
}
puts "PLL frequency is $pll_freq"
set_property -dict [list CONFIG.CLKOUT1_REQUESTED_OUT_FREQ $pll_freq] [get_ips clk_pll]

update_compile_order -fileset sources_1

# If IP cores are used
if { [llength [get_ips]] != 0} {
    upgrade_ip [get_ips]

    foreach ip [get_ips] {
        create_ip_run [get_ips $ip]
    }

    set ip_runs [get_runs -filter {SRCSET != sources_1 && IS_SYNTHESIS && STATUS != "synth_design Complete!"}]
    
    if { [llength $ip_runs] != 0} {
        launch_runs -quiet -jobs $jobs_number {*}$ip_runs
        
        foreach r $ip_runs {
            wait_on_run $r
        }
    }

}

exit