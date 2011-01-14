package prantl.ant.eclipse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An extension of OutputStream writing every byte as a character in the
 * internal StringBuffer.
 * 
 * @since Ant-Eclipse 1.0
 * @author Ferdinand Prantl &lt;prantl@users.sourceforge.net&gt;
 */
class AppendableOutputStream extends OutputStream {

	private Appendable buffer;

	/**
	 * Creates a new instance of the output stream. Default constructor.
	 * 
	 * @param buffer
	 *            The output buffer to write into.
	 * @since Ant-Eclipse 1.0
	 */
	AppendableOutputStream(Appendable buffer) {
		this.buffer = buffer;
	}

	/**
	 * Stores the byte as a character in the internal buffer.
	 * 
	 * @see java.io.OutputStream#write(int)
	 * @since Ant-Eclipse 1.0
	 */
	@Override
	public void write(int b) {
		char c = (char) b;
		try {
			this.buffer.append(c);
		} catch (IOException e) {
			throw new RuntimeException(String.format("Uable to apend %s", c), e);
		}
	}
}