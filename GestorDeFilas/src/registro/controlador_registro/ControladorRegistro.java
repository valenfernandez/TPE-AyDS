package registro.controlador_registro;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import registro.vista_registro.I_VistaRegistro;
import registro.vista_registro.VistaRegistro;
import registro.vista_registro.VistaRegistroConfirmacion;

/**
 * Clase que hace de intermediario entre la interfaz de usuario y el servidor del sistema.
 * Controla los eventos que ocurren en la vista de registro de clientes, y cuando sea oportuno,
 * le informa al componente que gestiona la fila de clientes, el DNI de un cliente que se quiso registrar.
 *
 */
public class ControladorRegistro implements ActionListener {
	
	private int numTotem = 0;
	
	private int PORT = 2080; // puerto para realizar el registro de clientes (DNIs)
	private int PORT_3 = 3700; // puerto para avisar al monitor que se activo un totem
	
	private String ipServidor; // IP del servidor
	private String ipMonitor; // IP del monitor
	
	// el controlador tiene la referencia de todas las ventanas que "controla"
	private VistaRegistro vistaRegistro;
	private VistaRegistroConfirmacion vistaConfirmacion;
	
	// disponibilidad (t�ctica reintento)
	private int intentosRegistro = 2; //maxima cantidad de intentos para comunicarse con el servidor para realizar un registro.
	
	public ControladorRegistro(VistaRegistro vistaRegistro, VistaRegistroConfirmacion vistaConfirmacion, String ipServidor, String ipMonitor, int numTotem) {
		
		this.numTotem = numTotem;
		
		this.ipServidor = ipServidor;
		this.ipMonitor = ipMonitor;
		
		this.vistaRegistro = vistaRegistro;
		this.vistaRegistro.setControlador(this); //le indicamos a la vista que el controlador ser� su action listener
		this.vistaRegistro.mostrarNumTotem(String.valueOf(numTotem));
		this.vistaRegistro.abrirVentana();
		
		this.vistaConfirmacion = vistaConfirmacion;
		this.vistaConfirmacion.setControlador(this);
		// por defecto las ventanas permanecen ocultas, por eso no 'cerramos' esta
		
		// Creo que para evitar este eventual 'while(true)' [si nunca ejecutaramos el monitor], el monitor deberia hacer ping a todos los posibles totems y box*
		// que pueda haber (nosotros poner el limite), y cuando respondan los agregamos a la lista de activos, y cuando no, los sacamos.
		// En el caso de cambiar esto, tambien cambiarlo en ControladorAtencion.
		// [*en el caso de los box's, habria que hacerles ping, y que nos devuelvan el num de box. Si es -1 quiere decir que todavia no se habilit�]
		// a parte, tendriamos que ver como gestionar la desconexion de los boxes, onda ver como sacarlos de la lista de activos cuando pase.
		boolean avisado = false;
		while (!avisado)
			avisado = this.avisoActivacion(); // avisamos al monitor que hay una nueva instancia de 'registro' (totem)
		
	}
	
	public void actionPerformed(ActionEvent arg0) { //el controlador va a estar "escuchando" los eventos que ocurran en las vistas
		// ingreso del dni
		if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_0)) this.vistaRegistro.mostrarDigito("0");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_1)) this.vistaRegistro.mostrarDigito("1");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_2)) this.vistaRegistro.mostrarDigito("2");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_3)) this.vistaRegistro.mostrarDigito("3");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_4)) this.vistaRegistro.mostrarDigito("4");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_5)) this.vistaRegistro.mostrarDigito("5");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_6)) this.vistaRegistro.mostrarDigito("6");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_7)) this.vistaRegistro.mostrarDigito("7");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_8)) this.vistaRegistro.mostrarDigito("8");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_NUM_9)) this.vistaRegistro.mostrarDigito("9");
		else if(arg0.getActionCommand().equals(I_VistaRegistro.AC_BORRAR)) this.vistaRegistro.borrarUltimoDigito();
		// pasar a la otra ventanita
		else
			if(arg0.getActionCommand().equals(I_VistaRegistro.AC_REGISTRAR)) {
				String dni = this.vistaRegistro.getDniIngresado();
				this.vistaRegistro.setEnabled(false);
				this.vistaConfirmacion.mostrarDni(dni);
				this.vistaConfirmacion.abrirVentana();
			}
			// modificar o confirmar el dni ingresado
			else
				if(arg0.getActionCommand().equals(I_VistaRegistro.AC_MODIFICAR)) {
					this.vistaRegistro.setEnabled(true); //si cerramos la ventanita con la 'X', esto nunca se har�a. Salvo que deshabilitemos esa 'X'.
					this.vistaConfirmacion.cerrarVentana();
				}
				else
					if(arg0.getActionCommand().equals(I_VistaRegistro.AC_CONFIRMAR)) {
						String dni = this.vistaRegistro.getDniIngresado();
						this.vistaRegistro.setEnabled(true);
						this.vistaConfirmacion.cerrarVentana();
						this.enviarDNI(dni);
						//la ventana no llama como tal a este m�todo, mas bien es algo indirecto; es la que origina/provoca que se active
						//habria que ver si es coherente con el diagrama de secuencia, pero as� se aplicar�a el patron MVC.
					}
	}
	
	private void enviarDNI(String dni) {
		int longDNI = dni.length();
		if(longDNI == 7 || longDNI == 8) {
			this.registrarDNI(dni);
			this.vistaRegistro.limpiarCampoDNI();
		}
		else
			this.vistaRegistro.errorDNI(); // dni no valido
	}
	
	private void registrarDNI(String dni) { // va el mensaje a GestionFila (servidor)
		try {
			Socket socket = new Socket(this.ipServidor, PORT);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println(dni);
			String respuesta = in.readLine(); // el servidor nos responder� si el DNI existe o no en el repositorio de clientes.
			if(respuesta.equals("existe"))
				this.vistaRegistro.registroExitoso();
			else
				this.vistaRegistro.errorCliente(); // dni no existente
			
			this.intentosRegistro = 2; // pudimos comunicarnos con el servidor -> reiniciamos el contador de intentos.
			
			out.close();
			socket.close();
		}
		catch (Exception e) {
			//e.printStackTrace();
			if(this.intentosRegistro > 0) {
				this.intentosRegistro--; // quizas estaria bueno poner un sleep que abarque mas de los 30s de ping del monitor.
				this.registrarDNI(dni);
			}else
				this.vistaRegistro.errorConexion();
		}

	}
	
	public void cambiarServidor(String nuevaIP, int nuevoServer) { // lo usa ManejadorErroresRegistro
		this.ipServidor = nuevaIP;
		if(nuevoServer == 2)
			this.PORT = 3080;
		else 
			this.PORT = 2080;
		
	}
	
	private boolean avisoActivacion() { // avisamos al manejador de comp. act. del monitor, que hay una nueva instancia (num) de 'registro' (totem)
		boolean avisado = false;
		try {
			Socket socket = new Socket(this.ipMonitor, PORT_3);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println("totem#" + this.numTotem);
			out.close();
			socket.close();
			avisado = true;
		}
		catch (Exception e) {
			avisado = false;
		}
		return avisado;
	}
	
	public int getNumTotem() {
		return this.numTotem;
	}

	
}
