import sys
import os
import subprocess
import threading
from queue import Queue, Empty

# Añadir el directorio de spotdl al path
spotdl_path = os.path.join(os.path.dirname(__file__), "spotdl")
sys.path.insert(0, spotdl_path)

# Importar spotdl después de añadir al path
from spotdl import Spotdl
from spotdl.download.downloader import Downloader
from spotdl.types.options import DownloaderOptionalOptions

# Cola para logs
log_queue = Queue()

def redirect_stdout_stderr():
    """
    Redirige stdout y stderr a la cola para capturar logs
    """
    class QueueWriter:
        def __init__(self, queue, stream_type):
            self.queue = queue
            self.stream_type = stream_type
            
        def write(self, text):
            if text.strip():
                self.queue.put((self.stream_type, text))
                
        def flush(self):
            pass

    sys.stdout = QueueWriter(log_queue, "stdout")
    sys.stderr = QueueWriter(log_queue, "stderr")

def get_logs():
    """
    Obtiene todos los logs disponibles de la cola
    """
    logs = []
    while True:
        try:
            log_type, message = log_queue.get_nowait()
            logs.append((log_type, message))
        except Empty:
            break
            
    return logs

def run_spotdl(args, temp_dir):
    """
    Ejecuta spotdl con los argumentos proporcionados
    
    Args:
        args: Lista de argumentos para spotdl
        temp_dir: Directorio temporal para descargas
        
    Returns:
        Código de salida de spotdl
    """
    try:
        # Configurar entorno
        os.chdir(temp_dir)
        
        # Redirigir stdout/stderr
        redirect_stdout_stderr()
        
        # Parsear argumentos
        spotdl_args = parse_arguments(args)
        
        # Configurar opciones de descarga
        downloader_options = DownloaderOptionalOptions(
            output=spotdl_args.get("output", "./"),
            format=spotdl_args.get("format", "mp3"),
            bitrate=spotdl_args.get("bitrate", None),
            ffmpeg=spotdl_args.get("ffmpeg", "ffmpeg"),
            threads=spotdl_args.get("threads", 1),
            variable_bitrate=spotdl_args.get("variable_bitrate", None),
            constant_bitrate=spotdl_args.get("constant_bitrate", None),
            log_level=spotdl_args.get("log_level", "INFO"),
            simple_tui=spotdl_args.get("simple_tui", False),
            print_errors=spotdl_args.get("print_errors", False),
            sponsor_block=spotdl_args.get("sponsor_block", False),
            preload=spotdl_args.get("preload", False),
            generate_lrc=spotdl_args.get("generate_lrc", False),
            force_update_metadata=spotdl_args.get("force_update_metadata", False),
            restrict=spotdl_args.get("restrict", False),
            detect_formats=spotdl_args.get("detect_formats", False),
            id3_separator=spotdl_args.get("id3_separator", "/"),
            ytm_data=spotdl_args.get("ytm_data", False),
            add_unavailable=spotdl_args.get("add_unavailable", False),
            set_cookie=spotdl_args.get("set_cookie", None),
            user_auth=spotdl_args.get("user_auth", False),
            headers=spotdl_args.get("headers", False),
            overwrite=spotdl_args.get("overwrite", "force"),
            port=spotdl_args.get("port", 8800),
            host=spotdl_args.get("host", "localhost"),
            keep_temp=spotdl_args.get("keep_temp", False),
            no_config=spotdl_args.get("no_config", False),
            only_verified_results=spotdl_args.get("only_verified_results", False),
            search_query=spotdl_args.get("search_query", None),
            filter_results=spotdl_args.get("filter_results", None),
            yt_dlp_args=spotdl_args.get("yt_dlp_args", None),
        )
        
        # Inicializar spotdl
        spotdl_client = Spotdl(
            client_id=spotdl_args.get("client_id", None),
            client_secret=spotdl_args.get("client_secret", None),
            user_auth=spotdl_args.get("user_auth", False),
            cache_path=spotdl_args.get("cache_path", None),
            no_config=spotdl_args.get("no_config", False),
            downloader_settings=downloader_options,
        )
        
        # Ejecutar comando
        if "download" in args:
            query = spotdl_args.get("query", [])
            if query:
                # Descargar canciones
                songs = spotdl_client.search(query)
                if songs:
                    downloader = Downloader(
                        output=downloader_options.output,
                        format=downloader_options.format,
                        bitrate=downloader_options.bitrate,
                        ffmpeg=downloader_options.ffmpeg,
                        variable_bitrate=downloader_options.variable_bitrate,
                        constant_bitrate=downloader_options.constant_bitrate,
                        log_level=downloader_options.log_level,
                        simple_tui=downloader_options.simple_tui,
                        print_errors=downloader_options.print_errors,
                        sponsor_block=downloader_options.sponsor_block,
                        preload=downloader_options.preload,
                        generate_lrc=downloader_options.generate_lrc,
                        force_update_metadata=downloader_options.force_update_metadata,
                        restrict=downloader_options.restrict,
                        detect_formats=downloader_options.detect_formats,
                        id3_separator=downloader_options.id3_separator,
                        ytm_data=downloader_options.ytm_data,
                        add_unavailable=downloader_options.add_unavailable,
                        set_cookie=downloader_options.set_cookie,
                        user_auth=downloader_options.user_auth,
                        headers=downloader_options.headers,
                        overwrite=downloader_options.overwrite,
                    )
                    
                    downloader.download_songs(songs)
                    return 0
                else:
                    print("No se encontraron canciones para la consulta")
                    return 1
        else:
            print("Comando no reconocido")
            return 1
            
    except Exception as e:
        print(f"Error ejecutando spotdl: {str(e)}")
        import traceback
        traceback.print_exc()
        return 1
        
    return 0

def parse_arguments(args):
    """
    Parsea los argumentos de línea de comandos
    
    Args:
        args: Lista de argumentos
        
    Returns:
        Diccionario con opciones parseadas
    """
    parsed_args = {}
    i = 0
    while i < len(args):
        arg = args[i]
        if arg.startswith("--"):
            key = arg[2:]
            if i + 1 < len(args) and not args[i + 1].startswith("--"):
                parsed_args[key] = args[i + 1]
                i += 1
            else:
                parsed_args[key] = True
        elif not arg.startswith("-"):
            if "query" not in parsed_args:
                parsed_args["query"] = []
            parsed_args["query"].append(arg)
        i += 1
        
    return parsed_args