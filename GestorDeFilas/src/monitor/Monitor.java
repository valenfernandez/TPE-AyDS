package monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Clase que monitorea a todos los componentes del sistema.
 * Si detecta que alguno no funciona, da aviso a los dem�s para que tomen la acci�n que corresponda.
 * Para esto, sabe la IP de las m�quinas donde se ejecuta cada componente, y los puertos que escuchan.
 * El Monitor es una t�ctica (Detecci�n de fallas) para implementar el atributo de calidad 'Disponibilidad'.
 * Hace uso de otra t�ctica llamada Ping/Echo, para justamente comunicarse con los componentes y esperar una respuesta (sockets).
 *
 */
public class Monitor {
	
	private static Monitor monitor = null;
	
	private static final int PORT_1 = 3200; // puerto para hacer ping al componente Llamado
	private static final int PORT_2 = 3210; // puerto para hacer ping al servidor primario
	private static final int PORT_3 = 3220; // puerto para hacer ping al servidor secundario
	private int PORT_4 = 4000; // puerto base para informar errores a todos los componentes Atencion activos (box's)
	private static final int PORT_5 = 3240; // puerto para informar errores al servidor primario
	private static final int PORT_6 = 3250; // puerto para informar errores al servidor secundario
	private int PORT_7 = 4100; // puerto base para informar errores a todos los componentes Registro activos (totems)
	private static final int PORT_9 = 3280; // puerto para avisar al servidor 2 que haga la resincronizacion.
	
	private String ipLlamado;
	private String ipServ1;
	private String ipServ2;
	private String ipAtencion;
	private String ipRegistro;
	
	private int servidorActivo = 1; // siempre por defecto el servidor activo es el primario (1).
	private ArrayList<Integer> box_activos = new ArrayList<Integer>();
	private ArrayList<Integer> totem_activos = new ArrayList<Integer>();
	private boolean llamadoEnLinea = true; // si hay conexion con la TV o no.
	
	private HiloMonitor hilo;
	
	private Monitor(String ipLlamado, String ipAtencion, String ipRegistro, String ipServ1, String ipServ2) {
		this.ipLlamado = ipLlamado;
		this.ipAtencion = ipAtencion;
		this.ipRegistro = ipRegistro;
		this.ipServ1 = ipServ1;
		this.ipServ2 = ipServ2;
		// instanciamos y activamos el hilo que har� ping's cada cierto tiempo.
		this.hilo = new HiloMonitor(this);
		this.hilo.start();
	}
	
	// Patron de Dise�o GoF: SINGLETON
	public static Monitor getMonitor(String ipLlamado, String ipAtencion, String ipRegistro, String ipServ1, String ipServ2) {
		if(monitor == null) 
			monitor = new Monitor(ipLlamado, ipAtencion, ipRegistro, ipServ1, ipServ2);
		return monitor;
	}
	
	public void agregarBoxActivo(int num) {
		this.box_activos.add(num);
		System.out.println("Se agreg� el box activo: " + num);
	}
	
	public void eliminarBoxActivo(int num) {
		int indice = this.box_activos.indexOf(num);
		this.box_activos.remove(indice);
		System.out.println("Se elimin� el box activo: " + num);
		// aviso al manejador de errores de atencion de ese box, que se tiene que cerrar (dejar de escuchar)
		// [es un peque�o hack para evitar un problema de puertos]
		try {
			Socket socket = new Socket(ipAtencion, PORT_4 + num);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String msg = "desactivar" + "#" + "ip_no_usada"; // "reaprovechamos" ese server socket
			out.println(msg);
			out.close();
			socket.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	// quizas podriamos hacer tambien un 'eliminarTotemActivo', pero habria que ver como asociar el evento de cerrar la ventana con la 'X'.
	public void agregarTotemActivo(int num) {
		this.totem_activos.add(num);
		System.out.println("Se agreg� el totem activo: " + num);
	}
	
	// PARA MI ESTE PING NO SER�A NECESARIO.
	// POR AHI LO QUE S� ESTAR�A BUENO ES QUE SOLO SEA UN PING COMO TAL, Y MOSTRAR POR CONSOLA SI EST� 'on' O NO.
	// Y LO MISMO PARA LOS COMPONENTES 'atencion' Y 'registro'. (igual para hacer algo as� el monitor deberia saber
	// todos las posibles IP y puerto de todos los totem y puestos de atencion que haya)
	// CON ESTO ULTIMO ME DI CUENTA QUE LOS 'avisos' PARA HACER EL CAMBIO DE SERVER, TENDRIA QUE SER A TODAS LAS POSIBLES IP-PUERTO
	// COSA QUE NOSOTROS LOCALMENTE COMO ES LA MISMA IP, DA ERROR AL POR EJEMPLO ABRIR 2 'atencion' YA QUE ESCUCHAN EL MISMO PUERTO EN LA MISMA IP.
	// ---arreglado---
	public void pingLlamado() { // t�ctica ping/echo al TV
		try {
			Socket socket = new Socket(ipLlamado, PORT_1);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println("ping");
			String msg = in.readLine();
			if(!msg.equals("ping")) { // error por recibir un mensaje no esperado
				this.avisoaAServ1("llamado"); // para que el servidor primario no mande llamados al TV
				this.avisoaAServ2("llamado"); // para que el servidor secundario no mande llamados al TV
				this.llamadoEnLinea = false;
				System.out.println("No hay conexi�n con la mini-pc (TV de llamados).");
			}else {
				if(!this.llamadoEnLinea) { //antes no andaba y ahora si. 
					this.llamadoEnLinea = true; // si no lo llegamos a usar en ninguna lado, despues sacarlo.
				}
			}
			out.close();
			socket.close();
		}
		catch (Exception e) { // error de comunicaci�n
			//e.printStackTrace();
			this.avisoaAServ1("llamado");
			this.avisoaAServ2("llamado");
			this.llamadoEnLinea = false;
			System.out.println("No hay conexi�n con la mini-pc (TV de llamados).");
		}
	}
	
	public void pingServidorPrimario() { // t�ctica ping/echo
		try {
			Socket socket = new Socket(ipServ1, PORT_2);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println("ping");
			String msg = in.readLine();
			if(!msg.equals("ping")) { // error por recibir un mensaje no esperado.
				this.avisoaAServ2("serv1"); // para que el servidor secundario se active (empiece a escuchar)
				this.avisoaAAtencion("serv1", this.ipServ2); // para que el componente atencion se empiece a comunicar con el servidor secundario
				this.avisoaARegistro("serv1", this.ipServ2); // para que el componente registro se empiece a comunicar con el servidor secundario
				this.servidorActivo = 2;
				System.out.println("No hay conexi�n con el servidor primario.");
			}
			else { //anduvo todo bien, primer servidor anda.
				if(this.servidorActivo == 2) { //antes no andaba
					this.avisoaAAtencion("serv2", ipServ1);
					this.avisoaARegistro("serv2", ipServ1);
					// estos 2 avisos realmente los usamos para indicar que volvi� a funcionar el servidor primario, y no que fall� el secundario.
					// no es que los componentes quedan conectados al secundario hasta que este se caiga, sino hasta que vuelva el primario.
					this.resincronizar(); //avisamos al servidor secundario que debe pasarle su fila de DNIs al primario.
					this.servidorActivo = 1;
					System.out.println("Volvi� la conexi�n con el servidor primario."); //estar�a bueno informar si la resincronizaci�n fue exitosa o no.
				}
				/*
				 * Puede ser que si las personas se siguen registrando mientras se hace la resincronizacion
				 * se mezcle el orden de los clientes. (los nuevos clientes van a quedar en el medio de la cola.
				 * ---
				 * PODRIAMOS LLEGAR A PREVENIR UN POCO MAS ESO SI PRIMERO MANDAMOS A RESINCRONIZAR Y LUEGO DAR LOS AVISOS DE CAMBIO DE SERVER
				 * Y EN RESINCRONIZAR PRIMERO DESHABILITAR EL SERVER2 Y LUEGO MANDAR LA FILA (cosa de que les pueda tirar un error de conexi�n).
				 */
			}
			out.close();
			socket.close();
		}
		catch (Exception e) { // error de comunicaci�n
			//e.printStackTrace();
			this.avisoaAServ2("serv1");
			this.avisoaAAtencion("serv1", this.ipServ2); // - Atencion, no anda el servidor primario, te paso la IP del secundario para que te conectes.
			this.avisoaARegistro("serv1", this.ipServ2);
			this.servidorActivo = 2;
			System.out.println("No hay conexi�n con el servidor primario.");
		}
	}
	
	// ESTE PING TAMPOCO SE BIEN SI ES NECESARIO, O SEA EL PING COMO TAL ESTARIA PARA TENERLO POR EL HECHO DE MONITOREAR.
	// PERO SI NO ANDA EL SERVIDOR2, CON TODO ESTO UNICAMENTE HACEMOS UN print.out.
	public void pingServidorSecundario() { // t�ctica ping/echo
		try {
			Socket socket = new Socket(ipServ2, PORT_3);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println("ping");
			String msg = in.readLine();
			if(!msg.equals("ping")) { // error por recibir un mensaje no esperado.
				this.avisoaAServ1("serv2"); // para que el servidor primario sepa que fall� el servidor 2 y no le siga mandando dni's.
				System.out.println("No hay conexi�n con el servidor secundario.");
				// NOTA: no damos aviso a los componentes que fall� el servidor secundario, porque por como lo implementamos nosotros,
				// los componentes siempre se conectar�n al servidor primario a menos que no est� funcionando. Entonces si los componentes
				// estaban conectados al secundario, y este falla, quiere decir que el primario tampoco anda, sino ya se hubieran reconectado a �l.
				// Entonces de nada sirve dar aviso que fall� el server secundario para que se conecten al primario, si este tampoco anda.
			}
			out.close();
			socket.close();
		}
		catch (Exception e) { // error de comunicaci�n
			//e.printStackTrace();
			this.avisoaAServ1("serv2");
			System.out.println("No hay conexi�n con el servidor secundario.");
		}
	}
	
	// - Atencion, no anda tal componente, conectate a esta IP.
	private void avisoaAAtencion(String componente, String ip) { // informar errores a todos los componentes 'atencion' activos
		int port = this.PORT_4;
		Iterator<Integer> it = this.box_activos.iterator();
		while (it.hasNext()) {
			int numBox = it.next();
			try {
				port += numBox;
				Socket socket = new Socket(ipAtencion, port);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String msg = componente + "#" + ip; // para despues saber como parsear el mensaje.
				out.println(msg);
				out.close();
				socket.close();
				port = this.PORT_4; // para que siempre se vaya sumando a partir del puerto base, y no acumulando.
			}
			catch (Exception e) {
				//e.printStackTrace();
				System.out.println("Se perdi� la conexi�n con el box " + numBox + ", por lo que no sabe que hubo alg�n fallo en el sistema.");
			}
		}
		
	}
	
	// - Registro, no anda tal componente, conectate a esta IP.
	private void avisoaARegistro(String componente, String ip) { // informar errores a todos los componentes 'registro' activos
		int port = this.PORT_7;
		Iterator<Integer> it = this.totem_activos.iterator();
		while (it.hasNext()) {
			int numTotem = it.next();
			try {
				port += numTotem;
				Socket socket = new Socket(this.ipRegistro, port);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String msg = componente + "#" + ip; // para despues saber como parsear el mensaje.
				out.println(msg);
				out.close();
				socket.close();
				port = this.PORT_7; // para que siempre se vaya sumando a partir del puerto base, y no acumulando.
			}
			catch (Exception e) {
				//e.printStackTrace();
				System.out.println("Se perdi� la conexi�n con el totem " + numTotem + ", por lo que no sabe que hubo alg�n fallo en el sistema.");
			}
		}
	}
	
	// - Servidor 1, no anda tal componente.
	private void avisoaAServ1(String componente) { // informar errores al servidor primario
		try {
			Socket socket = new Socket(this.ipServ1, PORT_5);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println(componente);
			out.close();
			socket.close();
		}
		catch (Exception e) {
			//e.printStackTrace();
			//si hubiera un error de conexion con el servidor primario, el monitor nos lo informar�a al hacerle ping.
			//despues ver si en todo caso aca informamos que no se pudo dar tal aviso al servidor 1.
		}
		
	}
	
	// - Servidor 2, no anda tal componente.
	private void avisoaAServ2(String componente) { // informar errores al servidor secundario
		try {
			Socket socket = new Socket(this.ipServ2, PORT_6);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println(componente);
			out.close();
			socket.close();
		}
		catch (Exception e) {
			//e.printStackTrace();
			//si hubiera un error de conexion con el servidor secundario, el monitor nos lo informar�a al hacerle ping.
			//despues ver si en todo caso aca informamos que no se pudo dar tal aviso al servidor 2.
		}
		
	}
	
	private void resincronizar() { // Avisamos al servidor secundario que debe pasarle su fila de DNIs al primario.
		try {
			Socket socket = new Socket(ipServ2, PORT_9);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println("res");
			out.close();
			socket.close();
		}
		catch (Exception e) {
			//e.printStackTrace();
			this.avisoaAServ1("serv2"); // si no hay conexion con el secundario, se lo avisamos al primario (quizas podriamos obviarlo)
		}
	}
	

}
