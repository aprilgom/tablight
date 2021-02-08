/*
 * Created: 2018-12-12 오전 10:23:01
 * Author : april
 */ 
/* This is AVR code for driving the RGB LED strips from Pololu.
   It allows complete control over the color of an arbitrary number of LEDs.
   This implementation disables interrupts while it does bit-banging with inline assembly.
 */

/* This line specifies the frequency your AVR is running at.
   This code supports 20 MHz, 16 MHz and 8MHz */
/* This is AVR code for driving the RGB LED strips from Pololu.
   It allows complete control over the color of an arbitrary number of LEDs.
   This implementation disables interrupts while it does bit-banging with inline assembly.
 */

//led 제어 코드는 https://github.com/pololu/pololu-led-strip-avr 에서 제공되는 것을 사용하였습니다.

/* This line specifies the frequency your AVR is running at.
   This code supports 20 MHz, 16 MHz and 8MHz */

//16Mhz에서 동작.
#define F_CPU 16000000UL

//led data핀 : PORT B의 0번핀 사용
#define LED_STRIP_PORT PORTB
#define LED_STRIP_DDR  DDRB
#define LED_STRIP_PIN  0

#include <avr/io.h>
#include <avr/interrupt.h>
#include <util/delay.h>


//한 led의 색상을 나타내기 위한 구조체입니다.
typedef struct rgb_color
{
  unsigned char red, green, blue;
} rgb_color;



void __attribute__((noinline)) led_strip_write(rgb_color * colors, unsigned int count) 
{
  // Set the pin to be an output driving low.
  LED_STRIP_PORT &= ~(1<<LED_STRIP_PIN);
  LED_STRIP_DDR |= (1<<LED_STRIP_PIN);

  cli();   // 타이밍이 어긋나게 되면 led의 색상이 제대로 표시되지 않으므로, 인터럽트를 막습니다.
  while(count--)
  {
    // colors 배열의 처음 포인터에서부터 시작하여 포인터를 증가시켜가며 g,r,b순으로 8비트씩 24비트를 led로 보냅니다.
    // 8비트는 msb-lsb 순으로 보냅니다.
    asm volatile(
        "ld __tmp_reg__, %a0+\n"
        "ld __tmp_reg__, %a0\n"
        "rcall send_led_strip_byte%=\n"  // green
        "ld __tmp_reg__, -%a0\n"
        "rcall send_led_strip_byte%=\n"  // red
        "ld __tmp_reg__, %a0+\n"
        "ld __tmp_reg__, %a0+\n"
        "ld __tmp_reg__, %a0+\n"
        "rcall send_led_strip_byte%=\n"  // blue
        "rjmp led_strip_asm_end%=\n"     // Jump past the assembly subroutines.

        // 8비트를 보내는 서브루틴.
        "send_led_strip_byte%=:\n"
        "rcall send_led_strip_bit%=\n"  // Send most-significant bit (bit 7).
        "rcall send_led_strip_bit%=\n"
        "rcall send_led_strip_bit%=\n"
        "rcall send_led_strip_bit%=\n"
        "rcall send_led_strip_bit%=\n"
        "rcall send_led_strip_bit%=\n"
        "rcall send_led_strip_bit%=\n"
        "rcall send_led_strip_bit%=\n"  // Send least-significant bit (bit 0).
        "ret\n"

        // led 모듈에 한 비트를 보내는 서브루틴입니다.
		// led 모듈에 많은 값을 전달하기 위해 하나의 핀이 몇초동안 켜지고 꺼지는가를 이용해 데이터를 전달합니다.
		// cpu 속도 : 16Mhz , 명령어 하나당 0.0625us의 시간이 소비됨.
		// 0을 보내는 경우 : 0.35 +- 0.15us 동안 1, 0.8 +- 0.15us 동안 0
		// 1을 보내는 경우 : 0.7 +- 0.15us 동안 1, 0.6 +- 0.15us 동안 0
		
		
        "send_led_strip_bit%=:\n"
#if F_CPU == 8000000
        "rol __tmp_reg__\n"                      // Rotate left through carry.
#endif
        "sbi %2, %3\n"                           // Drive the line high.

#if F_CPU != 8000000
        "rol __tmp_reg__\n"                      // Rotate left through carry.
#endif

#if F_CPU == 16000000
        "nop\n" "nop\n"
#elif F_CPU == 20000000
        "nop\n" "nop\n" "nop\n" "nop\n"
#elif F_CPU != 8000000
#error "Unsupported F_CPU"
#endif
		
        "brcs .+2\n" "cbi %2, %3\n"              // If the bit to send is 0, drive the line low now.

#if F_CPU == 8000000
        "nop\n" "nop\n"
#elif F_CPU == 16000000
        "nop\n" "nop\n" "nop\n" "nop\n" "nop\n"
#elif F_CPU == 20000000
        "nop\n" "nop\n" "nop\n" "nop\n" "nop\n"
        "nop\n" "nop\n"
#endif
	
        "brcc .+2\n" "cbi %2, %3\n"              // If the bit to send is 1, drive the line low now.

        "ret\n"
        "led_strip_asm_end%=: "
        : "=b" (colors)
        : "0" (colors),         // %a0 points to the next color to display
          "I" (_SFR_IO_ADDR(LED_STRIP_PORT)),   // %2 is the port register (e.g. PORTC)
          "I" (LED_STRIP_PIN)     // %3 is the pin number (0-8)
    );


  }
  sei();          // led 표시가 끝나고 인터럽트 제한을 해제합니다.
  _delay_us(50);  // 50us동안 0을 전달해 led를 리셋시킵니다. 리셋 후의 led의 색은 첫번째 led부터 바뀌게됩니다.
}

#define LED_COUNT 30
#include <avr/io.h>		// AVR 기본 include
#define NULL 0
#include <util/delay.h>

rgb_color colors[LED_COUNT];

void putchar1(char c)		// 1 char를 송신(Transmit)하는 함수
{
	while(!(UCSR1A & 0x20));	// UCSR0A 5번 비트 = UDRE
	UDR1 = c;			// 1 character 전송
}

char getchar1()			// 1 character를 수신(receive)하는 함수
{
	while (!(UCSR1A & 0x80));
		// UCSR0A 7번 비트 = RXC(Receiver Complete)
	return(UDR1);		// 1 character 수신
}

void puts1(char *ptr)	// string을 송신하는 함수
{
	while(1)
	{
		if (*ptr != NULL)	//  1글자씩 송신
		putchar1(*ptr++);
		else
		return;	// string 끝이면 종료
	}
}

int main()
{
	char *ptr = "end appeared";
	char c;
	DDRC = 0Xff;
	PORTC = 0Xff;
	PORTA = 0xff;
	
	UBRR1H=0;
	UBRR1L=8;
	// 16Mhz, 115200 baud
	UCSR1B = 0x18;	// Receive(RX) 및 Transmit(TX) Enable
	UCSR1C = 0x06;	// UART Mode, 8 Bit Data, No Parity, 1 Stop Bit
	char buffer[500];
	for(int k = 0;k < 120;k++){
		buffer[k] = 0;
	}
	int bufferi = 0;
	int colori = 0;
	char pixeli = 0;
	int color = 0;
	int rc = 0;
	int gc = 0;
	int bc = 0;
	while (1)
	{
		c = getchar1();		// 1 byte 수신.
		buffer[bufferi] = c;
		bufferi ++;
		if (c == '\r')		// led 데이터 끝?
		{
			
			colori = 0;
			pixeli = 0;
			
			color = 0;
			rc,gc,bc = 0;
			
			
			for(int k = 0;k<bufferi-1;k++){
				
				switch(buffer[k]){
					case 'r':
					rc = color;
					color = 0;
					break;
					case 'g':
					gc = color;
					color = 0;
					break;
					case 'b':
					bc = color;
					colors[pixeli] = (rgb_color){rc,gc,bc};
					rc,gc,bc = 0;
					pixeli++;
					color = 0;
					break;
					case '\r':
					break;
					default:
					color = color*10 + (buffer[k]-'0');
					break;
				}
				
			}
			bufferi = 0; //index를 처음으로 옮겨 새로쓰기.
			led_strip_write(colors, 30);
			putchar1('\n');		// led 처리 끝 문자.
			//puts1(ptr); for debug
			
		}
	}
}