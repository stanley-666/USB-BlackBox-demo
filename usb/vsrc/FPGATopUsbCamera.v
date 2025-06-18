
//--------------------------------------------------------------------------------------------------------
// Module  : FPGATopUsbCamera
// Type    : synthesizable, fpga top
// Standard: Verilog 2001 (IEEE1364-2001)
// Function: example for usb_camera_top
//--------------------------------------------------------------------------------------------------------

module FPGATopUsbCamera (
    // clock
    input  wire        clk50mhz,     // connect to a 50MHz oscillator
    // reset button
    input  wire        button,       // connect to a reset button, 0=reset, 1=release. If you don't have a button, tie this signal to 1.
    // LED
    output wire        led,          // 1: USB connected , 0: USB disconnected
    // USB signals
    output wire        usb_dp_pull,  // connect to USB D+ by an 1.5k resistor
    inout              usb_dp,       // connect to USB D+
    inout              usb_dn,       // connect to USB D-
    // debug output info, only for USB developers, can be ignored for normally use
    output wire        uart_tx       // If you want to see the debug info of USB device core, please connect this UART signal to host-PC (UART format: 115200,8,n,1), otherwise you can ignore this signal.
);




//-------------------------------------------------------------------------------------------------------------------------------------
// The USB controller core needs a 60MHz clock, this PLL module is to convert clk50mhz to clk60mhz
// This PLL module is only available on Altera Cyclone IV E.
// If you use other FPGA families, please use their compatible primitives or IP-cores to generate clk60mhz
//-------------------------------------------------------------------------------------------------------------------------------------
wire [3:0] subwire0;
wire       clk60mhz;
wire       clk_locked;

assign clk60mhz = clk50mhz;

//-------------------------------------------------------------------------------------------------------------------------------------
// USB-UVC camera device
//-------------------------------------------------------------------------------------------------------------------------------------

wire        vf_sof;
wire        vf_req;
reg  [ 7:0] vf_byte;

usb_camera_top #(
    .FRAME_TYPE      ( "MONO"              ),   // "MONO" or "YUY2"
    .FRAME_W         ( 14'd252             ),   // video-frame width  in pixels, must be a even number
    .FRAME_H         ( 14'd120             ),   // video-frame height in pixels, must be a even number
    .DEBUG           ( "FALSE"             )    // If you want to see the debug info of USB device core, set this parameter to "TRUE"
) u_usb_camera (
    .rstn            ( clk_locked & button ),
    .clk             ( clk60mhz            ),
    // USB signals
    .usb_dp_pull     ( usb_dp_pull         ),
    .usb_dp          ( usb_dp              ),
    .usb_dn          ( usb_dn              ),
    // USB reset output
    .usb_rstn        ( led                 ),   // 1: connected , 0: disconnected (when USB cable unplug, or when system reset (rstn=0))
    // video frame fetch interface
    .vf_sof          ( vf_sof              ),
    .vf_req          ( vf_req              ),
    .vf_byte         ( vf_byte             ),
    // debug output info, only for USB developers, can be ignored for normally use
    .debug_en        (                     ),
    .debug_data      (                     ),
    .debug_uart_tx   ( uart_tx             )
);




//-------------------------------------------------------------------------------------------------------------------------------------
// generate pixels
//-------------------------------------------------------------------------------------------------------------------------------------
reg  [7:0] init_pixel = 8'h00;

always @ (posedge clk60mhz)
    if (vf_sof) begin                          // at start of frame
        init_pixel <= init_pixel + 8'h1;
        vf_byte <= init_pixel;
    end else if (vf_req) begin                 // request a pixel
        vf_byte <= vf_byte + 8'h1;
    end



endmodule
