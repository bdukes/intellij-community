'''An helper file for the pydev debugger (REPL) console
'''
from code import InteractiveConsole
import sys
import traceback

import _pydev_completer
from pydevd_tracing import GetExceptionTracebackStr
from pydevd_vars import makeValidXmlValue
from pydev_imports import Exec
from pydevd_io import IOBuf
from pydev_console_utils import BaseInterpreterInterface, BaseStdIn
from pydev_override import overrides
import pydevd_save_locals

CONSOLE_OUTPUT = "output"
CONSOLE_ERROR = "error"


#=======================================================================================================================
# ConsoleMessage
#=======================================================================================================================
class ConsoleMessage:
    """Console Messages
    """
    def __init__(self):
        self.more = False
        # List of tuple [('error', 'error_message'), ('message_list', 'output_message')]
        self.console_messages = []

    def add_console_message(self, message_type, message):
        """add messages in the console_messages list
        """
        for m in message.split("\n"):
            if m.strip():
                self.console_messages.append((message_type, m))

    def update_more(self, more):
        """more is set to true if further input is required from the user
        else more is set to false
        """
        self.more = more

    def toXML(self):
        """Create an XML for console message_list, error and more (true/false)
        <xml>
            <message_list>console message_list</message_list>
            <error>console error</error>
            <more>true/false</more>
        </xml>
        """
        makeValid = makeValidXmlValue

        xml = '<xml><more>%s</more>' % (self.more)

        for message_type, message in self.console_messages:
            xml += '<%s message="%s"></%s>' % (message_type, makeValid(message), message_type)

        xml += '</xml>'

        return xml


#=======================================================================================================================
# DebugConsoleStdIn
#=======================================================================================================================
class DebugConsoleStdIn(BaseStdIn):

    overrides(BaseStdIn.readline)
    def readline(self, *args, **kwargs):
        sys.stderr.write('Warning: Reading from stdin is still not supported in this console.\n')
        return '\n'

#=======================================================================================================================
# DebugConsole
#=======================================================================================================================
class DebugConsole(InteractiveConsole, BaseInterpreterInterface):
    """Wrapper around code.InteractiveConsole, in order to send
    errors and outputs to the debug console
    """

    overrides(BaseInterpreterInterface.createStdIn)
    def createStdIn(self):
        return DebugConsoleStdIn() #For now, raw_input is not supported in this console.


    overrides(InteractiveConsole.push)
    def push(self, line, frame):
        """Change built-in stdout and stderr methods by the
        new custom StdMessage.
        execute the InteractiveConsole.push.
        Change the stdout and stderr back be the original built-ins

        Return boolean (True if more input is required else False),
        output_messages and input_messages
        """
        more = False
        original_stdout = sys.stdout
        original_stderr = sys.stderr
        try:
            try:
                self.frame = frame
                out = sys.stdout = IOBuf()
                err = sys.stderr = IOBuf()
                more = self.addExec(line)
            except Exception:
                exc = GetExceptionTracebackStr()
                err.buflist.append("Internal Error: %s" % (exc,))
        finally:
            #Remove frame references.
            self.frame = None
            frame = None
            sys.stdout = original_stdout
            sys.stderr = original_stderr

        return more, out.buflist, err.buflist


    overrides(BaseInterpreterInterface.doAddExec)
    def doAddExec(self, line):
        return InteractiveConsole.push(self, line)


    overrides(InteractiveConsole.runcode)
    def runcode(self, code):
        """Execute a code object.

        When an exception occurs, self.showtraceback() is called to
        display a traceback.  All exceptions are caught except
        SystemExit, which is reraised.

        A note about KeyboardInterrupt: this exception may occur
        elsewhere in this code, and may not always be caught.  The
        caller should be prepared to deal with it.

        """
        try:
            Exec(code, self.frame.f_globals, self.frame.f_locals)
            pydevd_save_locals.save_locals(self.frame)
        except SystemExit:
            raise
        except:
            self.showtraceback()


#=======================================================================================================================
# InteractiveConsoleCache
#=======================================================================================================================
class InteractiveConsoleCache:

    thread_id = None
    frame_id = None
    interactive_console_instance = None


#Note: On Jython 2.1 we can't use classmethod or staticmethod, so, just make the functions below free-functions.
def get_interactive_console(thread_id, frame_id, frame, console_message):
    """returns the global interactive console.
    interactive console should have been initialized by this time
    """
    if InteractiveConsoleCache.thread_id == thread_id and InteractiveConsoleCache.frame_id == frame_id:
        return InteractiveConsoleCache.interactive_console_instance

    InteractiveConsoleCache.interactive_console_instance = DebugConsole()
    InteractiveConsoleCache.thread_id = thread_id
    InteractiveConsoleCache.frame_id = frame_id

    console_stacktrace = traceback.extract_stack(frame, limit=1)
    if console_stacktrace:
        current_context = console_stacktrace[0] # top entry from stacktrace
        context_message = 'File "%s", line %s, in %s' % (current_context[0], current_context[1], current_context[2])
        console_message.add_console_message(CONSOLE_OUTPUT, "[Current context]: %s" % (context_message,))
    return InteractiveConsoleCache.interactive_console_instance


def clear_interactive_console():
    InteractiveConsoleCache.thread_id = None
    InteractiveConsoleCache.frame_id = None
    InteractiveConsoleCache.interactive_console_instance = None


def execute_console_command(frame, thread_id, frame_id, line):
    """fetch an interactive console instance from the cache and
    push the received command to the console.

    create and return an instance of console_message
    """
    console_message = ConsoleMessage()

    interpreter = get_interactive_console(thread_id, frame_id, frame, console_message)
    more, output_messages, error_messages = interpreter.push(line, frame)
    console_message.update_more(more)

    for message in output_messages:
        console_message.add_console_message(CONSOLE_OUTPUT, message)

    for message in error_messages:
        console_message.add_console_message(CONSOLE_ERROR, message)

    return console_message


def get_completions(frame, act_tok):
    """ fetch all completions, create xml for the same
    return the completions xml
    """
    return _pydev_completer.GenerateCompletionsAsXML(frame, act_tok)





